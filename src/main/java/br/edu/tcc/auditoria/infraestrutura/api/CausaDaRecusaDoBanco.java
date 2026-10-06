package br.edu.tcc.auditoria.infraestrutura.api;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Classe que escreve a causa de uma recusa do banco (D019). Usa só a primeira linha da mensagem do PostgreSQL: as seguintes, como Detail, trazem o valor gravado, que pode ser chave de acesso com CNPJ dentro.
final class CausaDaRecusaDoBanco {

    // Código do PostgreSQL para "raise exception" sem código próprio, que é o que os gatilhos deste sistema usam.
    private static final String RECUSA_DE_GATILHO = "P0001";

    // Classe 23 do SQLSTATE: violação de restrição de integridade.
    private static final String CLASSE_DE_INTEGRIDADE = "23";

    // O que vem antes do texto, em inglês e em português, conforme o idioma do servidor.
    private static final Pattern PREFIXO_DO_SERVIDOR = Pattern.compile("^(ERROR|ERRO):\\s*");

    // Último nome entre aspas da linha, que nas mensagens de restrição é o nome dela.
    private static final Pattern NOME_ENTRE_ASPAS = Pattern.compile("\"([A-Za-z0-9_]+)\"");

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private CausaDaRecusaDoBanco() {
    }

    // Método estático que escreve a causa da recusa a partir da exceção do Spring.
    static String de(Throwable recusa) {
        Optional<SQLException> doBanco = sqlExceptionEm(recusa);
        String codigo = doBanco.map(SQLException::getSQLState).orElse(null);
        String primeiraLinha = doBanco.map(CausaDaRecusaDoBanco::primeiraLinha).orElse("");

        if (RECUSA_DE_GATILHO.equals(codigo) && !primeiraLinha.isBlank()) {
            return "O banco recusou a gravação: " + primeiraLinha;
        }
        if (codigo != null && codigo.startsWith(CLASSE_DE_INTEGRIDADE)) {
            String restricao = restricaoEm(primeiraLinha)
                    .map(nome -> "a restrição \"%s\" foi violada".formatted(nome))
                    .orElse("uma restrição de integridade foi violada");
            return ("O banco recusou a gravação: %s (código SQL %s). Isso não é concorrência, e repetir o "
                    + "mesmo pedido terá o mesmo resultado: o pedido traz dado inconsistente com o que já está "
                    + "gravado, ou o sistema tem um defeito. O restante da mensagem do banco não é repassado "
                    + "porque pode conter dado do documento.").formatted(restricao, codigo);
        }
        return ("O banco recusou a gravação (%s). A causa não pôde ser identificada, e a mensagem do banco não "
                + "é repassada porque pode conter dado do documento. Não repita o pedido sem entender a causa: "
                + "o registro do servidor tem a exceção completa.")
                .formatted(codigo == null ? "sem código SQL" : "código SQL " + codigo);
    }

    // Método auxiliar que acha a SQLException na cadeia de causas.
    private static Optional<SQLException> sqlExceptionEm(Throwable recusa) {
        for (Throwable atual = recusa; atual != null; atual = atual.getCause()) {
            if (atual instanceof SQLException doBanco) {
                return Optional.of(doBanco);
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return Optional.empty();
    }

    // Método auxiliar que devolve a primeira linha da mensagem, sem o prefixo do servidor.
    private static String primeiraLinha(SQLException doBanco) {
        String mensagem = doBanco.getMessage() == null ? "" : doBanco.getMessage();
        String linha = mensagem.lines().findFirst().orElse("").strip();
        return PREFIXO_DO_SERVIDOR.matcher(linha).replaceFirst("");
    }

    // Método auxiliar que devolve o nome da restrição, quando a linha fala de uma.
    private static Optional<String> restricaoEm(String linha) {
        String minuscula = linha.toLowerCase(java.util.Locale.ROOT);
        if (!minuscula.contains("constraint") && !minuscula.contains("restrição")) {
            return Optional.empty();
        }
        Matcher nomes = NOME_ENTRE_ASPAS.matcher(linha);
        String ultimo = null;
        while (nomes.find()) {
            ultimo = nomes.group(1);
        }
        return Optional.ofNullable(ultimo);
    }
}
