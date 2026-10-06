package br.edu.tcc.auditoria.infraestrutura.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Classe que escreve a recusa do filtro de segurança no mesmo formato de erro do resto da API: 401 para quem não entrou, 403 para quem entrou mas não tem o perfil. O JSON é escrito à mão, com dois campos fixos, para este pacote não depender de Jackson nem de Spring MVC, que ficam só em infraestrutura/api (D009).
final class RespostaDeRecusa implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String SEM_SESSAO =
            "É preciso entrar com login e senha para usar o sistema.";

    static final String SEM_PERMISSAO =
            "O seu perfil não tem permissão para isto. A recusa é do servidor, e vale mesmo chamando "
                    + "o endereço direto.";

    static final String TOKEN_INVALIDO =
            "O pedido veio sem o token contra falsificação, ou com um token vencido. Recarregue a página "
                    + "e tente de novo.";

    // Responde 401 a quem não entrou.
    @Override
    public void commence(
            HttpServletRequest pedido, HttpServletResponse resposta, AuthenticationException recusa)
            throws IOException {
        escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, "SEM_SESSAO", SEM_SESSAO);
    }

    // Responde 403 a quem entrou mas não pode, e a quem mandou pedido de escrita sem o token.
    @Override
    public void handle(
            HttpServletRequest pedido, HttpServletResponse resposta, AccessDeniedException recusa)
            throws IOException {
        boolean token = recusa instanceof CsrfException;
        escrever(resposta, HttpServletResponse.SC_FORBIDDEN,
                token ? "TOKEN_INVALIDO" : "SEM_PERMISSAO",
                token ? TOKEN_INVALIDO : SEM_PERMISSAO);
    }

    // Escreve o corpo de erro em JSON, com os campos erro e mensagem.
    void escrever(HttpServletResponse resposta, int situacao, String codigo, String mensagem)
            throws IOException {
        String corpo = "{\"erro\":\"" + escapar(codigo) + "\",\"mensagem\":\"" + escapar(mensagem) + "\"}";
        resposta.setStatus(situacao);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.setContentType("application/json");
        resposta.getOutputStream().write(corpo.getBytes(StandardCharsets.UTF_8));
    }

    // Método auxiliar que escapa aspas, barra invertida e caracteres de controle para o texto caber numa string JSON.
    static String escapar(String texto) {
        StringBuilder escapado = new StringBuilder(texto.length());
        for (char caractere : texto.toCharArray()) {
            switch (caractere) {
                case '"' -> escapado.append("\\\"");
                case '\\' -> escapado.append("\\\\");
                case '\n' -> escapado.append("\\n");
                case '\r' -> escapado.append("\\r");
                case '\t' -> escapado.append("\\t");
                default -> {
                    if (caractere < 0x20) {
                        escapado.append(String.format("\\u%04x", (int) caractere));
                    } else {
                        escapado.append(caractere);
                    }
                }
            }
        }
        return escapado.toString();
    }
}
