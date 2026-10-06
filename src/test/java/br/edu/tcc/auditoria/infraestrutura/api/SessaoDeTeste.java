package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Abre uma sessão de verdade para os testes que falam com a API por HTTP.
 *
 * <p>Acrescentado na Etapa 12. Com autenticação, todo endpoint passou a exigir
 * sessão, e os testes de contrato das Etapas 8 e 11 passariam a receber 401. A
 * saída recusada foi um perfil de teste com a segurança desligada: ele tornaria
 * falso dizer que a segurança está testada. Aqui a sessão é aberta pelo mesmo
 * caminho do navegador — token contra falsificação, POST /api/sessao — e o
 * cookie e o token passam a ir em todo pedido do {@link TestRestTemplate}.</p>
 *
 * <p>A senha é fictícia e só existe dentro do contêiner descartável do teste.</p>
 */
final class SessaoDeTeste {

    static final String SENHA = "senha-ficticia-de-teste-0000";

    private SessaoDeTeste() {
    }

    /** O login do usuário de teste de cada perfil. */
    static String loginDe(Perfil perfil) {
        return "teste." + perfil.name().toLowerCase(Locale.ROOT);
    }

    /** Garante que o usuário do perfil existe, ativo, e devolve-o. */
    static Usuario garantirUsuario(ServicoDeUsuarios usuarios, Perfil perfil) {
        String login = loginDe(perfil);
        Optional<Usuario> existente = usuarios.listar().stream()
                .filter(usuario -> usuario.login().equals(login))
                .findFirst();
        if (existente.isPresent()) {
            return existente.get();
        }
        return usuarios.criar(login, "Usuário de teste " + perfil.rotulo(), perfil, new SenhaInformada(SENHA));
    }

    /**
     * Entra com o usuário do perfil e faz o cliente mandar a sessão e o token em
     * todo pedido seguinte. Troca a sessão anterior, se houver.
     */
    static void entrarComo(TestRestTemplate cliente, ServicoDeUsuarios usuarios, Perfil perfil) {
        garantirUsuario(usuarios, perfil);
        sair(cliente);
        Credencial credencial = abrirSessao(cliente, loginDe(perfil), SENHA);
        cliente.getRestTemplate().getInterceptors().add(credencial);
    }

    /** Tira do cliente a sessão instalada por {@link #entrarComo}. */
    static void sair(TestRestTemplate cliente) {
        cliente.getRestTemplate().getInterceptors().removeIf(Credencial.class::isInstance);
    }

    /** Abre a sessão e devolve o cookie e o token; recusa se o login falhar. */
    static Credencial abrirSessao(TestRestTemplate cliente, String login, String senha) {
        sair(cliente);
        ResponseEntity<String> token = cliente.getForEntity("/api/sessao/csrf", String.class);
        String cookieAnonimo = cookieDaSessao(token.getHeaders());
        JsonNode corpoDoToken = ler(token.getBody());
        String cabecalho = corpoDoToken.get("cabecalho").asText();
        String valor = corpoDoToken.get("token").asText();

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.add(HttpHeaders.COOKIE, cookieAnonimo);
        cabecalhos.add(cabecalho, valor);
        String corpo = "{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}";
        ResponseEntity<String> entrada = cliente.exchange(
                "/api/sessao", HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
        if (!entrada.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "A sessão de teste não abriu: " + entrada.getStatusCode() + " " + entrada.getBody());
        }
        String cookieDaSessao = Optional.ofNullable(cookieDaSessao(entrada.getHeaders())).orElse(cookieAnonimo);
        return new Credencial(cookieDaSessao, cabecalho, valor);
    }

    private static String cookieDaSessao(HttpHeaders cabecalhos) {
        List<String> definidos = cabecalhos.get(HttpHeaders.SET_COOKIE);
        if (definidos == null) {
            return null;
        }
        return definidos.stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElse(null);
    }

    private static JsonNode ler(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException naoEraJson) {
            throw new IllegalStateException("Resposta do token não é JSON: " + json, naoEraJson);
        }
    }

    /** Põe o cookie da sessão e o token em todo pedido. */
    record Credencial(String cookie, String cabecalhoDoToken, String token)
            implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                HttpRequest pedido, byte[] corpo, ClientHttpRequestExecution execucao) throws IOException {
            pedido.getHeaders().set(HttpHeaders.COOKIE, cookie);
            pedido.getHeaders().set(cabecalhoDoToken, token);
            return execucao.execute(pedido, corpo);
        }
    }
}
