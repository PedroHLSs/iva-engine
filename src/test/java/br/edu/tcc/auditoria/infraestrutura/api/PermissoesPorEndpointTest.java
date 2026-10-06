package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.infraestrutura.seguranca.MatrizDePermissoes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda permissão é conferida no servidor, e este teste prova isso chamando cada
 * endpoint direto, por HTTP, com cada perfil — sem passar por tela nenhuma.
 *
 * <p>A matriz esperada está escrita À MÃO aqui, e não lida de
 * {@link MatrizDePermissoes}. Ler de lá tornaria o teste circular: uma permissão
 * alargada por engano na matriz de produção seria alargada junto na expectativa.
 * Em vez disso, o teste confere três coisas contra esta tabela:</p>
 *
 * <ol>
 *   <li>a matriz de produção é igual a esta, linha a linha;</li>
 *   <li>todo endpoint que o Spring de fato registrou sob {@code /api} está nesta
 *       tabela, e toda linha desta tabela é um endpoint registrado — endpoint novo
 *       sem permissão decidida quebra o build;</li>
 *   <li>chamando de verdade, quem está na tabela passa pelo filtro, e quem não está
 *       recebe 403 — e quem não entrou recebe 401.</li>
 * </ol>
 *
 * <p>"Passa pelo filtro" quer dizer: a resposta não é 401 nem 403. Pode ser 400 ou
 * 404, porque os identificadores aqui são inventados; o que se mede é a
 * autorização, e não o caso de uso.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "spring.jackson.default-property-inclusion=always",
                "auditoria.tolerancia-de-valor=0.01",
                "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa"
        })
@Testcontainers
@EnabledIf("dockerDisponivel")
class PermissoesPorEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Set<Perfil> TODOS = EnumSet.allOf(Perfil.class);
    private static final Set<Perfil> FISCAL_E_ADMIN = EnumSet.of(Perfil.FISCAL, Perfil.ADMINISTRADOR);
    private static final Set<Perfil> SO_ADMIN = EnumSet.of(Perfil.ADMINISTRADOR);

    /** A matriz esperada, escrita à mão. Método e caminho como o Spring registra. */
    private static final Map<String, Set<Perfil>> ESPERADA = new TreeMap<>(Map.ofEntries(
            Map.entry("GET /api/sessao", TODOS),
            Map.entry("DELETE /api/sessao", TODOS),
            Map.entry("PUT /api/sessao/senha", TODOS),
            Map.entry("GET /api/execucoes", TODOS),
            Map.entry("GET /api/execucoes/{id}", TODOS),
            Map.entry("GET /api/execucoes/{id}/achados", TODOS),
            Map.entry("GET /api/execucoes/{id}/nao-avaliados", TODOS),
            Map.entry("GET /api/analises", TODOS),
            Map.entry("GET /api/analises/{id}", TODOS),
            Map.entry("GET /api/analises/{id}/produtos", TODOS),
            Map.entry("GET /api/analises/{id}/produtos/{endereco}", TODOS),
            Map.entry("GET /api/analises/{id}/grupos", TODOS),
            Map.entry("GET /api/analises/{id}/grupos/produtos", TODOS),
            Map.entry("GET /api/analises/{id}/vinculos", TODOS),
            Map.entry("GET /api/analises/{id}/autoria", TODOS),
            Map.entry("GET /api/base-tributaria", TODOS),
            Map.entry("GET /api/achados/{id}/tratativas", TODOS),
            Map.entry("GET /api/cargas", TODOS),
            Map.entry("GET /api/cargas/{versao}", TODOS),
            Map.entry("GET /api/acuracia/previa", TODOS),
            Map.entry("POST /api/acuracia", FISCAL_E_ADMIN),
            Map.entry("POST /api/analises", FISCAL_E_ADMIN),
            Map.entry("POST /api/analises/{id}/correcoes", FISCAL_E_ADMIN),
            Map.entry("POST /api/achados/{id}/tratativas", FISCAL_E_ADMIN),
            Map.entry("POST /api/cargas", SO_ADMIN),
            Map.entry("PUT /api/cargas/{versao}", SO_ADMIN),
            Map.entry("DELETE /api/cargas/{versao}", SO_ADMIN),
            Map.entry("GET /api/usuarios", SO_ADMIN),
            Map.entry("POST /api/usuarios", SO_ADMIN),
            Map.entry("GET /api/usuarios/{id}", SO_ADMIN),
            Map.entry("PUT /api/usuarios/{id}", SO_ADMIN),
            Map.entry("DELETE /api/usuarios/{id}", SO_ADMIN)));

    /** Abertos sem sessão: entrar e pegar o token. */
    private static final Set<String> SEM_SESSAO = Set.of("POST /api/sessao", "GET /api/sessao/csrf");

    /** Registro de auditoria: nenhum perfil altera nem apaga. */
    private static final List<String> NUNCA = List.of(
            "PUT /api/execucoes/{id}",
            "DELETE /api/execucoes/{id}",
            "PUT /api/analises/{id}",
            "DELETE /api/analises/{id}",
            "PUT /api/execucoes/{id}/achados",
            "DELETE /api/execucoes/{id}/achados",
            "PUT /api/achados/{id}",
            "DELETE /api/achados/{id}",
            "PUT /api/achados/{id}/tratativas",
            "DELETE /api/achados/{id}/tratativas");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ServicoDeUsuarios usuarios;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void aMatrizDeProducaoDeveSerIgualAEscritaAMaoNoTeste() {
        Map<String, Set<Perfil>> producao = new TreeMap<>();
        for (MatrizDePermissoes.Permissao permissao : MatrizDePermissoes.COM_SESSAO) {
            producao.put(permissao.metodo().name() + " " + permissao.caminho(), permissao.perfis());
        }
        Set<String> abertas = new TreeSet<>();
        for (MatrizDePermissoes.Permissao aberta : MatrizDePermissoes.SEM_SESSAO) {
            abertas.add(aberta.metodo().name() + " " + aberta.caminho());
        }

        assertThat(producao).isEqualTo(ESPERADA);
        assertThat(abertas).isEqualTo(new TreeSet<>(SEM_SESSAO));
    }

    @Test
    void todoEndpointRegistradoDeveTerPermissaoDecididaENaoHaPermissaoSemEndpoint() {
        Set<String> registrados = new TreeSet<>();
        for (RequestMappingInfo info : mapeamentos.getHandlerMethods().keySet()) {
            for (String caminho : info.getPatternValues()) {
                if (!caminho.startsWith("/api")) {
                    continue;
                }
                info.getMethodsCondition().getMethods()
                        .forEach(metodo -> registrados.add(metodo.name() + " " + caminho));
            }
        }
        Set<String> decididos = new TreeSet<>(ESPERADA.keySet());
        decididos.addAll(SEM_SESSAO);

        assertThat(registrados)
                .as("autoverificação: a varredura precisa ter encontrado os endpoints das Etapas 8 e 11")
                .contains("GET /api/execucoes", "POST /api/analises");
        assertThat(registrados)
                .as("endpoint sem permissão decidida: acrescente-o à matriz, com os perfis, antes de publicá-lo")
                .isSubsetOf(decididos);
        assertThat(decididos)
                .as("permissão para endpoint que não existe: a matriz e os controladores divergiram")
                .isSubsetOf(registrados);
    }

    @Test
    void cadaPerfilDeveSerRecusadoPeloServidorOndeNaoTemPermissaoMesmoChamandoDireto() {
        List<String> falhas = new ArrayList<>();
        for (Perfil perfil : Perfil.values()) {
            SessaoDeTeste.entrarComo(rest, usuarios, perfil);
            for (Map.Entry<String, Set<Perfil>> linha : ESPERADA.entrySet()) {
                if (linha.getKey().equals("DELETE /api/sessao")) {
                    continue;
                }
                int situacao = chamar(linha.getKey()).getStatusCode().value();
                boolean pode = linha.getValue().contains(perfil);
                if (pode && (situacao == 401 || situacao == 403)) {
                    falhas.add("%s deveria passar para %s, mas recebeu %d".formatted(linha.getKey(), perfil, situacao));
                }
                if (!pode && situacao != 403) {
                    falhas.add("%s deveria ser recusado para %s com 403, mas recebeu %d"
                            .formatted(linha.getKey(), perfil, situacao));
                }
            }
        }
        assertThat(falhas).isEmpty();
    }

    @Test
    void aRecusaDoPerfilDeveVirDoServidorComCodigoProprio() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.CONSULTA);

        ResponseEntity<String> resposta = chamar("POST /api/achados/{id}/tratativas");

        assertThat(resposta.getStatusCode().value()).isEqualTo(403);
        assertThat(resposta.getBody()).contains("\"erro\":\"SEM_PERMISSAO\"");
    }

    @Test
    void quemNaoEntrouDeveReceber401EmTodoEndpointProtegido() {
        SessaoDeTeste.Credencial anonima = sessaoAnonima();
        rest.getRestTemplate().getInterceptors().add(anonima);

        List<String> falhas = new ArrayList<>();
        for (String endpoint : ESPERADA.keySet()) {
            int situacao = chamar(endpoint).getStatusCode().value();
            if (situacao != 401) {
                falhas.add("%s sem sessão respondeu %d, e não 401".formatted(endpoint, situacao));
            }
        }
        assertThat(falhas).isEmpty();
    }

    @Test
    void execucaoEAchadoNaoTemPutNemDeleteParaNenhumPerfil() {
        List<String> falhas = new ArrayList<>();
        for (Perfil perfil : Perfil.values()) {
            SessaoDeTeste.entrarComo(rest, usuarios, perfil);
            for (String endpoint : NUNCA) {
                int situacao = chamar(endpoint).getStatusCode().value();
                if (situacao != 403) {
                    falhas.add("%s como %s respondeu %d, e não 403".formatted(endpoint, perfil, situacao));
                }
            }
        }
        assertThat(falhas).isEmpty();

        Set<String> registrados = new TreeSet<>();
        mapeamentos.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().forEach(caminho ->
                info.getMethodsCondition().getMethods().forEach(metodo -> registrados.add(metodo + " " + caminho))));
        assertThat(registrados)
                .as("nenhum controlador pode registrar alteração ou exclusão de execução, análise ou achado")
                .noneMatch(endpoint -> endpoint.matches("(PUT|DELETE|PATCH) /api/(execucoes|analises|achados).*"));
    }

    @Test
    void escritaSemOTokenContraFalsificacaoDeveSerRecusada() {
        SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);
        SessaoDeTeste.Credencial credencial =
                SessaoDeTeste.abrirSessao(rest, SessaoDeTeste.loginDe(Perfil.FISCAL), SessaoDeTeste.SENHA);

        HttpHeaders soOCookie = new HttpHeaders();
        soOCookie.add(HttpHeaders.COOKIE, credencial.cookie());
        soOCookie.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.exchange(
                "/api/achados/" + UUID.randomUUID() + "/tratativas", HttpMethod.POST,
                new HttpEntity<>("{\"decisao\":\"ACEITO\",\"justificativa\":\"x\"}", soOCookie), String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(403);
        assertThat(resposta.getBody()).contains("TOKEN_INVALIDO");
    }

    @Test
    void sairDeveEncerrarASessao() {
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.FISCAL);
        assertThat(rest.getForEntity("/api/sessao", String.class).getStatusCode().value()).isEqualTo(200);

        assertThat(chamar("DELETE /api/sessao").getStatusCode().value()).isEqualTo(204);

        assertThat(rest.getForEntity("/api/sessao", String.class).getStatusCode().value()).isEqualTo(401);
    }

    /** Pega um token numa sessão anônima, para a recusa por falta de login não se confundir com a de falta de token. */
    private SessaoDeTeste.Credencial sessaoAnonima() {
        ResponseEntity<String> token = rest.getForEntity("/api/sessao/csrf", String.class);
        String cookie = token.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(valor -> valor.startsWith("JSESSIONID="))
                .map(valor -> valor.split(";", 2)[0])
                .findFirst().orElseThrow();
        String corpo = token.getBody();
        String valor = corpo.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        String cabecalho = corpo.replaceAll("(?s).*\"cabecalho\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return new SessaoDeTeste.Credencial(cookie, cabecalho, valor);
    }

    /** Chama "MÉTODO /caminho" com identificadores inventados e corpo vazio em JSON. */
    private ResponseEntity<String> chamar(String endpoint) {
        String[] partes = endpoint.split(" ", 2);
        String caminho = partes[1]
                .replace("{id}", UUID.randomUUID().toString())
                .replace("{endereco}", "a".repeat(64))
                .replace("{versao}", "versao-que-nao-existe");
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        HttpMethod metodo = HttpMethod.valueOf(partes[0]);
        HttpEntity<String> pedido = metodo == HttpMethod.GET
                ? new HttpEntity<>(cabecalhos)
                : new HttpEntity<>("{}", cabecalhos);
        try {
            return rest.exchange(caminho, metodo, pedido, String.class);
        } catch (org.springframework.web.client.ResourceAccessException semCorpo) {
            // O cliente HTTP do JDK não devolve resposta 401 a pedido com corpo: lança HttpRetryException, que traz o código recebido.
            if (semCorpo.getCause() instanceof java.net.HttpRetryException recusa) {
                return ResponseEntity.status(recusa.responseCode()).build();
            }
            throw semCorpo;
        }
    }
}
