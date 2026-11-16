package br.com.anotaai.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class PromptInjectionValidator {
    private static final Pattern[] INDICIOS = {
            Pattern.compile(
                    "\\b(ignore|ignorem|desconsidere)\\s+(todas?\\s+)?(as?\\s+)?(instrucoes|regras|orientacoes)\\b"),
            Pattern.compile(
                    "\\b(revele|mostre|exiba|imprima)\\s+(seu|suas|o|as)?\\s*(prompt|instrucoes internas|system prompt)\\b"),
            Pattern.compile(
                    "\\b(you are now|voce agora e|ignore previous instructions|disregard previous instructions)\\b"),
            Pattern.compile("\\b(system|developer)\\s*:\\s*(ignore|override|execute|revele)\\b")
    };

    private PromptInjectionValidator() {
    }

    static boolean possuiIndicios(String texto) {
        if (texto == null || texto.isEmpty())
            return false;
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        for (Pattern padrao : INDICIOS) {
            if (padrao.matcher(normalizado).find())
                return true;
        }
        return false;
    }
}
