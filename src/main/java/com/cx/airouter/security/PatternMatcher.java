package com.cx.airouter.security;

import com.cx.airouter.config.AgentRoutingProperties;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** 預先編譯的具名正規表示式集合；比對前做 NFKC 正規化，降低以全形字元繞過的機會。 */
final class PatternMatcher {

    private record Compiled(String name, Pattern pattern) {
    }

    private final List<Compiled> patterns;

    PatternMatcher(List<AgentRoutingProperties.NamedPattern> source) {
        this.patterns = source.stream()
                .filter(p -> p.getRegex() != null && !p.getRegex().isBlank())
                .map(p -> new Compiled(p.getName(),
                        Pattern.compile(p.getRegex(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
                .toList();
    }

    /** 回傳第一個符合的規則名稱（只回傳名稱，不回傳符合內容）。 */
    Optional<String> firstMatch(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return patterns.stream()
                .filter(p -> p.pattern().matcher(normalized).find())
                .map(Compiled::name)
                .findFirst();
    }
}
