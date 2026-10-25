#!/usr/bin/env python3
"""批次評估路由分類：送出標註題庫、記錄 route / confidence / 延遲，輸出準確率、混淆矩陣與門檻曲線。

建議以 AI_ROUTER_CONFIDENCE_THRESHOLD=0 啟動服務，才能取得低信心題目的原始路由，
再於本腳本離線模擬不同門檻（門檻以下一律視為 UNCERTAIN，與 RouteResolver 相同）。

用法：python3 scripts/eval-routing.py eval/phase1-questions.jsonl --out <輸出目錄>
"""
import argparse
import json
import os
import statistics
import time
import urllib.error
import urllib.request

ROUTES = ["FINANCE", "PRODUCTION", "BOTH", "OTHER", "UNCERTAIN"]
AGENT_ROUTES = {"FINANCE", "PRODUCTION", "BOTH"}
THRESHOLDS = [0.0, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95]


def ask(base_url, api_key, question):
    body = json.dumps({"question": question}).encode("utf-8")
    req = urllib.request.Request(f"{base_url}/api/ai/ask", data=body, method="POST", headers={
        "Content-Type": "application/json", "X-API-Key": api_key})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            status, payload = resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        status = e.code
        try:
            payload = json.loads(e.read())
        except ValueError:
            payload = {}
    return status, payload, round((time.perf_counter() - start) * 1000)


def final_route(row, threshold):
    """依門檻模擬 RouteResolver：信心不足一律 UNCERTAIN。"""
    if row["route"] is None:
        return None
    if row["route"] != "UNCERTAIN" and (row["confidence"] or 0) < threshold:
        return "UNCERTAIN"
    return row["route"]


def percentile(values, p):
    values = sorted(values)
    k = (len(values) - 1) * p / 100
    lo, hi = int(k), min(int(k) + 1, len(values) - 1)
    return round(values[lo] + (values[hi] - values[lo]) * (k - lo))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dataset")
    ap.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    ap.add_argument("--api-key", default=os.environ.get("DEMO_API_KEY_ALL", "demo-all-key"))
    ap.add_argument("--out", default="eval/results")
    args = ap.parse_args()

    with open(args.dataset, encoding="utf-8") as f:
        items = [json.loads(line) for line in f if line.strip()]
    os.makedirs(args.out, exist_ok=True)

    rows = []
    for item in items:
        status, payload, client_ms = ask(args.base_url, args.api_key, item["question"])
        accept = item.get("accept", [item["expected"]])
        rows.append({
            **item, "accept": accept, "http": status,
            "route": payload.get("route"), "confidence": payload.get("confidence"),
            "classifier": payload.get("classifier"), "durationMs": payload.get("durationMs"),
            "clientMs": client_ms, "reason": payload.get("reason"), "error": payload.get("errorCode"),
            "warnings": payload.get("warnings"),
        })
        print(f"{item['id']:4} {item['expected']:10} → {str(rows[-1]['route']):10} "
              f"conf={rows[-1]['confidence']} {client_ms}ms HTTP {status}", flush=True)

    with open(os.path.join(args.out, "results.jsonl"), "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    routed = [r for r in rows if not r["expected"].startswith("HTTP_")]
    security = [r for r in rows if r["expected"].startswith("HTTP_")]
    md = [f"# 路由評估結果（{len(rows)} 題）", ""]

    # 原始分類（門檻 0）準確率
    md += ["## 各類別準確率（原始分類，不套門檻）", "", "| 類別 | 題數 | 正確 | 準確率 |", "|---|---|---|---|"]
    for cat in dict.fromkeys(r["category"] for r in routed):
        group = [r for r in routed if r["category"] == cat]
        ok = sum(final_route(r, 0) in r["accept"] for r in group)
        md.append(f"| {cat} | {len(group)} | {ok} | {ok / len(group):.0%} |")
    ok = sum(final_route(r, 0) in r["accept"] for r in routed)
    md += [f"| **合計** | {len(routed)} | {ok} | {ok / len(routed):.0%} |", ""]

    # 混淆矩陣（主要預期標籤 × 原始分類）
    md += ["## 混淆矩陣（列：預期，欄：實際，門檻 0）", "",
           "| 預期 \\ 實際 | " + " | ".join(ROUTES) + " |", "|---" * (len(ROUTES) + 1) + "|"]
    for exp in ROUTES:
        cells = [sum(1 for r in routed if r["expected"] == exp and r["route"] == act) for act in ROUTES]
        md.append(f"| {exp} | " + " | ".join(str(c) for c in cells) + " |")
    md.append("")

    # 門檻曲線
    answerable = [r for r in routed if r["expected"] != "UNCERTAIN"]
    md += ["## 門檻曲線", "",
           "誤路由：呼叫了錯的 Agent（最需要避免）；過度保守：應可回答卻判 UNCERTAIN。", "",
           "| 門檻 | 準確率 | 誤路由 | 過度保守 |", "|---|---|---|---|"]
    for t in THRESHOLDS:
        acc = sum(final_route(r, t) in r["accept"] for r in routed)
        mis = sum(final_route(r, t) in AGENT_ROUTES and final_route(r, t) not in r["accept"] for r in routed)
        over = sum(final_route(r, t) == "UNCERTAIN" for r in answerable)
        md.append(f"| {t:.2f} | {acc / len(routed):.0%} | {mis} | {over}/{len(answerable)} |")
    md.append("")

    # 以路由結果評估：OTHER 與 UNCERTAIN 都不呼叫 Agent，視為同一結果
    agent_qs = [r for r in routed if any(a in AGENT_ROUTES for a in r["accept"])]
    non_agent_qs = [r for r in routed if r not in agent_qs]
    md += ["## 路由結果（OTHER 與 UNCERTAIN 視為同一種「不呼叫 Agent」）", "",
           f"應呼叫 Agent 的題目 {len(agent_qs)} 題；不應呼叫的 {len(non_agent_qs)} 題。", "",
           "| 門檻 | 正確 Agent | BOTH 只呼叫一個 | 錯誤 Agent | 被擋下 | 非業務題誤呼叫 |",
           "|---|---|---|---|---|---|"]
    for t in THRESHOLDS:
        ok = partial = wrong = blocked = 0
        for r in agent_qs:
            f = final_route(r, t)
            if f in r["accept"]:
                ok += 1
            elif f not in AGENT_ROUTES:
                blocked += 1
            elif r["expected"] == "BOTH":
                partial += 1
            else:
                wrong += 1
        leak = sum(final_route(r, t) in AGENT_ROUTES for r in non_agent_qs)
        md.append(f"| {t:.2f} | {ok} | {partial} | {wrong} | {blocked} | {leak} |")
    md.append("")

    # confidence 分佈
    confs = [r["confidence"] for r in routed if r["confidence"] is not None]
    bins = [(0, 0.5), (0.5, 0.75), (0.75, 0.9), (0.9, 0.99), (0.99, 1.01)]
    md += ["## confidence 分佈", "", "| 區間 | 題數 |", "|---|---|"]
    for lo, hi in bins:
        md.append(f"| {lo:.2f}–{min(hi, 1):.2f}{'' if hi > 1 else ' (不含)'} | {sum(lo <= c < hi for c in confs)} |")
    md.append("")

    # 延遲
    lat = [r["durationMs"] for r in rows if r["durationMs"] is not None]
    md += ["## 延遲（伺服器 durationMs）", "",
           f"- 題數 {len(lat)}，平均 {round(statistics.mean(lat))} ms，p50 {percentile(lat, 50)} ms，"
           f"p95 {percentile(lat, 95)} ms，最大 {max(lat)} ms", ""]

    if security:
        md += ["## 安全防護題", "", "| ID | 預期 | 實際 HTTP | 錯誤碼 |", "|---|---|---|---|"]
        md += [f"| {r['id']} | {r['expected']} | {r['http']} | {r['error']} |" for r in security]
        md.append("")

    wrong = [r for r in routed if final_route(r, 0) not in r["accept"]]
    md += ["## 分錯的題目（原始分類）", "", "| ID | 問題 | 預期 | 實際 | confidence |", "|---|---|---|---|---|"]
    md += [f"| {r['id']} | {r['question']} | {'/'.join(r['accept'])} | {r['route']} | {r['confidence']} |"
           for r in wrong] or ["| – | 無 | | | |"]
    md.append("")

    path = os.path.join(args.out, "summary.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(md))
    print("\n".join(md))
    print(f"\n已寫入 {path}")


if __name__ == "__main__":
    main()
