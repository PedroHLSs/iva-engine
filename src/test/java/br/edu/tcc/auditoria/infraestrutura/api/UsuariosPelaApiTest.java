package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O CRUD de usuários pela API, contra PostgreSQL de verdade.
 *
 * <p>Cobre as três regras que não podem morar na tela: o último administrador ativo
 * não sai nem é rebaixado; quem já registrou tratativa é desativado, e não
 * apagado; e a senha não aparece em resposta nem em log.</p>
 *
 * <p>O log é ligado em nível de detalhe para o Spring Security e o Spring MVC, e o
 * teste confere que ele de fato escreveu sobre os pedidos — sem isso, "a senha não
 * aparece no log" passaria por não haver log nenhum.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "spring.jackson.default-property-inclusion=always",
                "auditoria.tolerancia-de-valor=0.01",
                "auditoria.pseudonimizacao.sal=sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa",
                "logging.level.org.springframework.security=TRACE",
                "logging.level.org.springframework.web=DEBUG",
                "logging.level.br.edu.tcc.auditoria=DEBUG",
                "spring.mvc.log-request-details=true",
                // O cliente do próprio teste loga, em DEBUG, o corpo que ele envia, com a senha dentro. Isso é o
                // teste falando, e não o sistema: o que se confere aqui é o log do servidor.
                "logging.level.org.springframework.web.client=INFO",
                // O logger de reserva que o cliente usa quando o dele está desligado.
                "logging.level.org.springframework.web.HttpLogging=INFO"
        })
@Testcontainers
@EnabledIf("dockerDisponivel")
@ExtendWith(OutputCaptureExtension.class)
class UsuariosPelaApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SENHA_QUE_NAO_PODE_VAZAR = "senha-que-nao-pode-vazar-7391";
    private static final String OUTRA_SENHA_QUE_NAO_PODE_VAZAR = "outra-senha-que-nao-vaza-4826";
    private static final Pattern FORMA_DE_BCRYPT = Pattern.compile("\\$2[aby]?\\$\\d{2}\\$");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ServicoDeUsuarios usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> respostas = new ArrayList<>();

    static boolean dockerDisponivel() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException semDocker) {
            return false;
        }
    }

    @BeforeEach
    void entrarComoAdministrador() {
        jdbc.execute("truncate table usuario cascade");
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
    }

    @AfterEach
    void sair() {
        SessaoDeTeste.sair(rest);
    }

    @Test
    void naoDeveExcluirOUltimoAdministradorAtivo() {
        Usuario unico = SessaoDeTeste.garantirUsuario(usuarios, Perfil.ADMINISTRADOR);

        ResponseEntity<String> resposta = pedir(HttpMethod.DELETE, "/api/usuarios/" + unico.id(), null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody()).contains("ULTIMO_ADMINISTRADOR").contains("único administrador ativo");
        assertThat(usuarios.buscar(unico.id()).ativo()).isTrue();
    }

    @Test
    void naoDeveRebaixarNemDesativarOUltimoAdministradorAtivo() {
        Usuario unico = SessaoDeTeste.garantirUsuario(usuarios, Perfil.ADMINISTRADOR);

        ResponseEntity<String> rebaixar = pedir(HttpMethod.PUT, "/api/usuarios/" + unico.id(), "{\"perfil\":\"FISCAL\"}");
        ResponseEntity<String> desativar = pedir(HttpMethod.PUT, "/api/usuarios/" + unico.id(), "{\"ativo\":false}");

        assertThat(rebaixar.getStatusCode().value()).isEqualTo(409);
        assertThat(desativar.getStatusCode().value()).isEqualTo(409);
        Usuario gravado = usuarios.buscar(unico.id());
        assertThat(gravado.perfil()).isEqualTo(Perfil.ADMINISTRADOR);
        assertThat(gravado.ativo()).isTrue();
    }

    @Test
    void deveCriarListarAlterarEExcluirUsuario() {
        ResponseEntity<String> criado = pedir(HttpMethod.POST, "/api/usuarios", """
                {"login":"fiscal.novo","nome":"Fiscal Ficticio","perfil":"FISCAL","senha":"%s"}"""
                .formatted(SENHA_QUE_NAO_PODE_VAZAR));
        assertThat(criado.getStatusCode().value()).isEqualTo(201);
        String id = campo(criado.getBody(), "id");

        assertThat(pedir(HttpMethod.GET, "/api/usuarios", null).getBody()).contains("fiscal.novo");

        ResponseEntity<String> alterado = pedir(HttpMethod.PUT, "/api/usuarios/" + id,
                "{\"perfil\":\"CONSULTA\",\"nome\":\"Consulta Ficticia\"}");
        assertThat(alterado.getBody()).contains("\"perfil\":\"CONSULTA\"").contains("Consulta Ficticia");

        ResponseEntity<String> excluido = pedir(HttpMethod.DELETE, "/api/usuarios/" + id, null);
        assertThat(excluido.getBody()).contains("REMOVIDO");
        assertThat(pedir(HttpMethod.GET, "/api/usuarios/" + id, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void excluirQuemJaRegistrouTratativaDeveDesativarENaoApagar() {
        Usuario fiscal = SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);
        registrarTratativaNoHistorico(fiscal.id());

        ResponseEntity<String> resposta = pedir(HttpMethod.DELETE, "/api/usuarios/" + fiscal.id(), null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).contains("DESATIVADO").contains("identificável");
        Usuario gravado = usuarios.buscar(fiscal.id());
        assertThat(gravado.ativo()).isFalse();
        assertThat(jdbc.queryForObject(
                "select count(*) from tratativa_registro where autor_id = ?", Integer.class, fiscal.id()))
                .as("a tratativa continua atribuída a ele")
                .isEqualTo(1);
    }

    @Test
    void oBancoDeveRecusarApagarQuemJaRegistrouTratativaMesmoSemPassarPeloServico() {
        Usuario fiscal = SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);
        registrarTratativaNoHistorico(fiscal.id());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("delete from usuario where id = ?", fiscal.id()))
                .as("ON DELETE RESTRICT é a segunda barreira")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void oHistoricoDeTratativaDeveRecusarAlteracaoEExclusaoNoBanco() {
        Usuario fiscal = SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);
        registrarTratativaNoHistorico(fiscal.id());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("update tratativa_registro set justificativa = 'outra'"))
                .hasMessageContaining("só recebe acréscimo");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("delete from tratativa_registro"))
                .hasMessageContaining("só recebe acréscimo");
    }

    @Test
    void usuarioDesativadoDevePerderASessaoNoPedidoSeguinte() {
        SessaoDeTeste.garantirUsuario(usuarios, Perfil.FISCAL);
        SessaoDeTeste.Credencial doFiscal = SessaoDeTeste.abrirSessao(
                rest, SessaoDeTeste.loginDe(Perfil.FISCAL), SessaoDeTeste.SENHA);
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        Usuario fiscal = usuarios.listar().stream()
                .filter(usuario -> usuario.login().equals(SessaoDeTeste.loginDe(Perfil.FISCAL))).findFirst().orElseThrow();

        pedir(HttpMethod.PUT, "/api/usuarios/" + fiscal.id(), "{\"ativo\":false}");

        SessaoDeTeste.sair(rest);
        rest.getRestTemplate().getInterceptors().add(doFiscal);
        ResponseEntity<String> depois = rest.getForEntity("/api/execucoes", String.class);
        assertThat(depois.getStatusCode().value()).isEqualTo(401);
        assertThat(depois.getBody()).contains("SESSAO_ENCERRADA");
    }

    @Test
    void rebaixarDeveValerNoPedidoSeguinteSemPrecisarEntrarDeNovo() {
        // Um administrador rebaixado, com a sessão aberta ainda como administrador. A listagem de usuários
        // não tem conferência de perfil no serviço: só o filtro, relendo o usuário, pode recusá-la.
        pedir(HttpMethod.POST, "/api/usuarios", """
                {"login":"admin.dois","nome":"Admin dois","perfil":"ADMINISTRADOR","senha":"%s"}"""
                .formatted(SessaoDeTeste.SENHA));
        SessaoDeTeste.Credencial doSegundo = SessaoDeTeste.abrirSessao(rest, "admin.dois", SessaoDeTeste.SENHA);
        SessaoDeTeste.entrarComo(rest, usuarios, Perfil.ADMINISTRADOR);
        Usuario segundo = usuarios.listar().stream()
                .filter(usuario -> usuario.login().equals("admin.dois")).findFirst().orElseThrow();

        pedir(HttpMethod.PUT, "/api/usuarios/" + segundo.id(), "{\"perfil\":\"FISCAL\"}");

        SessaoDeTeste.sair(rest);
        rest.getRestTemplate().getInterceptors().add(doSegundo);
        assertThat(rest.getForEntity("/api/usuarios", String.class).getStatusCode().value())
                .as("a sessão aberta como administrador não carrega o perfil antigo depois do rebaixamento")
                .isEqualTo(403);
        assertThat(rest.getForEntity("/api/execucoes", String.class).getStatusCode().value())
                .as("e continua valendo para o que o perfil novo pode")
                .isEqualTo(200);
    }

    @Test
    void aSenhaNaoDeveAparecerEmRespostaNemEmLog(CapturedOutput log) {
        ResponseEntity<String> criado = pedir(HttpMethod.POST, "/api/usuarios", """
                {"login":"fiscal.senha","nome":"Fiscal Ficticio","perfil":"FISCAL","senha":"%s"}"""
                .formatted(SENHA_QUE_NAO_PODE_VAZAR));
        String id = campo(criado.getBody(), "id");
        pedir(HttpMethod.GET, "/api/usuarios", null);
        pedir(HttpMethod.GET, "/api/usuarios/" + id, null);
        pedir(HttpMethod.PUT, "/api/usuarios/" + id,
                "{\"novaSenha\":\"%s\"}".formatted(OUTRA_SENHA_QUE_NAO_PODE_VAZAR));

        SessaoDeTeste.sair(rest);
        rest.getRestTemplate().getInterceptors().add(
                SessaoDeTeste.abrirSessao(rest, "fiscal.senha", OUTRA_SENHA_QUE_NAO_PODE_VAZAR));
        respostas.add(rest.getForEntity("/api/sessao", String.class).getBody());
        respostas.add(pedir(HttpMethod.PUT, "/api/sessao/senha",
                "{\"senhaAtual\":\"%s\",\"senhaNova\":\"%s\"}"
                        .formatted("senha-atual-errada-0000", SENHA_QUE_NAO_PODE_VAZAR)).getBody());
        SessaoDeTeste.sair(rest);
        respostas.add(pedir(HttpMethod.POST, "/api/sessao",
                "{\"login\":\"fiscal.senha\",\"senha\":\"%s\"}".formatted(SENHA_QUE_NAO_PODE_VAZAR)).getBody());

        assertThat(log.getAll())
                .as("autoverificação: o log precisa ter escrito sobre os pedidos, senão não prova nada")
                .contains("/api/usuarios")
                .contains("/api/sessao")
                .as("autoverificação: o servidor logou o corpo lido, e é por ele que a senha vazaria")
                .contains("Read \"application/json");
        assertThat(String.join("\n", respostas))
                .as("autoverificação: as respostas foram capturadas")
                .contains("fiscal.senha");

        for (String senha : List.of(SENHA_QUE_NAO_PODE_VAZAR, OUTRA_SENHA_QUE_NAO_PODE_VAZAR)) {
            assertThat(log.getAll()).as("senha no log").doesNotContain(senha);
            assertThat(String.join("\n", respostas)).as("senha em resposta").doesNotContain(senha);
        }
        assertThat(FORMA_DE_BCRYPT.matcher(String.join("\n", respostas)).find())
                .as("o hash também não sai em resposta")
                .isFalse();
        assertThat(FORMA_DE_BCRYPT.matcher(log.getAll()).find())
                .as("o hash também não sai em log")
                .isFalse();
    }

    @Test
    void aSenhaDeveSerGravadaComoHashComSalProprio() {
        pedir(HttpMethod.POST, "/api/usuarios", """
                {"login":"fiscal.um","nome":"Fiscal um","perfil":"FISCAL","senha":"%s"}"""
                .formatted(SENHA_QUE_NAO_PODE_VAZAR));
        pedir(HttpMethod.POST, "/api/usuarios", """
                {"login":"fiscal.dois","nome":"Fiscal dois","perfil":"FISCAL","senha":"%s"}"""
                .formatted(SENHA_QUE_NAO_PODE_VAZAR));

        List<String> hashes = jdbc.queryForList(
                "select hash_senha from usuario where login in ('fiscal.um', 'fiscal.dois')", String.class);

        assertThat(hashes).hasSize(2).allMatch(hash -> FORMA_DE_BCRYPT.matcher(hash).find());
        assertThat(hashes).noneMatch(hash -> hash.contains(SENHA_QUE_NAO_PODE_VAZAR));
        assertThat(hashes.get(0))
                .as("mesma senha, sal diferente, hash diferente")
                .isNotEqualTo(hashes.get(1));
    }

    private void registrarTratativaNoHistorico(UUID autorId) {
        jdbc.update("insert into tratativa_registro (id, hash_item, regra_id, regra_versao, decisao, justificativa,"
                        + " registrado_em, autor_id) values (?, ?, 'RXX', '0.0.0-ficticia', 'ACEITO',"
                        + " 'Justificativa ficticia', ?, ?)",
                UUID.randomUUID(), "f".repeat(64), Timestamp.from(Instant.parse("1900-01-01T00:00:00Z")), autorId);
    }

    private ResponseEntity<String> pedir(HttpMethod metodo, String caminho, String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.exchange(caminho, metodo,
                corpo == null ? new HttpEntity<>(cabecalhos) : new HttpEntity<>(corpo, cabecalhos), String.class);
        respostas.add(resposta.getBody() == null ? "" : resposta.getBody());
        return resposta;
    }

    private static String campo(String json, String nome) {
        Matcher achado = Pattern.compile("\"" + nome + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(achado.find()).as("campo %s em %s", nome, json).isTrue();
        return achado.group(1);
    }
}
