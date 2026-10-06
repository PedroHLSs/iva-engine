package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.identidade.CodificadorDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.HashDeSenha;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;
import br.edu.tcc.auditoria.aplicacao.tratativa.AutorDaTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.HistoricoDeTratativas;
import br.edu.tcc.auditoria.aplicacao.tratativa.RegistroDeTratativa;
import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativaAtribuida;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os dois comandos da Etapa 12 que pedem senha no terminal: criar-administrador e
 * tratar-achado. O terminal é falso, para o teste poder dizer o que foi digitado —
 * ou que não há terminal nenhum.
 */
class ComandosDeIdentidadeTest {

    private static final String SENHA = "senha-ficticia-de-terminal-0000";
    private static final UUID ACHADO_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final TerminalFalso terminal = new TerminalFalso();
    private final List<String> linhas = new ArrayList<>();
    private final Saida saida = linhas::add;
    private final List<Tratativa> gravadas = new ArrayList<>();
    private UsuariosEmMemoria repositorio;
    private ServicoDeUsuarios usuarios;

    @BeforeEach
    void preparar() {
        repositorio = new UsuariosEmMemoria();
        usuarios = new ServicoDeUsuarios(repositorio, new CodificadorFalso(),
                Clock.fixed(Instant.parse("1900-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void criarAdministradorDevePedirASenhaDuasVezesENuncaEscreveLa() {
        terminal.digitar(SENHA, SENHA);

        comandoCriar().executar(Argumentos.de("criar-administrador", "--login=admin.local", "--nome=Admin"));

        assertThat(usuarios.autenticar("admin.local", new SenhaInformada(SENHA)))
                .hasValueSatisfying(usuario -> assertThat(usuario.perfil()).isEqualTo(Perfil.ADMINISTRADOR));
        assertThat(String.join("\n", linhas)).contains("criado").doesNotContain(SENHA);
    }

    @Test
    void criarAdministradorDeveRecusarSenhasDiferentesSemGravarNada() {
        terminal.digitar(SENHA, "outra-senha-diferente-0000");

        assertThatThrownBy(() -> comandoCriar().executar(
                Argumentos.de("criar-administrador", "--login=admin.local", "--nome=Admin")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("não são iguais");
        assertThat(usuarios.listar()).isEmpty();
    }

    @Test
    void semTerminalOsComandosRecusamEmVezDeLerASenhaDeOutroLugar() {
        assertThatThrownBy(() -> comandoCriar().executar(
                Argumentos.de("criar-administrador", "--login=admin.local", "--nome=Admin")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("terminal interativo");
    }

    @Test
    void aSenhaNaoEntraComoOpcaoDaLinhaDeComando() {
        assertThatThrownBy(() -> comandoCriar().executar(Argumentos.de(
                "criar-administrador", "--login=admin.local", "--nome=Admin", "--senha=" + SENHA)))
                .isInstanceOf(UsoInvalido.class);
    }

    @Test
    void tratarAchadoDeveGravarEmNomeDeQuemEntrou() {
        usuarios.criar("fiscal.local", "Fiscal Ficticio", Perfil.FISCAL, new SenhaInformada(SENHA));
        terminal.digitar(SENHA);

        comandoTratar().executar(Argumentos.de("tratar-achado", "--usuario=fiscal.local",
                "--achado=" + ACHADO_ID, "--decisao=ACEITO", "--justificativa=Justificativa ficticia"));

        assertThat(gravadas).hasSize(1);
        assertThat(String.join("\n", linhas)).contains("decidido por: Fiscal Ficticio (fiscal.local)");
    }

    @Test
    void tratarAchadoComSenhaErradaNaoDeveGravarNada() {
        usuarios.criar("fiscal.local", "Fiscal Ficticio", Perfil.FISCAL, new SenhaInformada(SENHA));
        terminal.digitar("senha-errada-qualquer-0000");

        assertThatThrownBy(() -> comandoTratar().executar(Argumentos.de("tratar-achado", "--usuario=fiscal.local",
                "--achado=" + ACHADO_ID, "--decisao=ACEITO", "--justificativa=Justificativa ficticia")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("Login ou senha inválidos");
        assertThat(gravadas).isEmpty();
    }

    @Test
    void consultaNaoDeveTratarAchadoNemPelaLinhaDeComando() {
        usuarios.criar("consulta.local", "Consulta Ficticia", Perfil.CONSULTA, new SenhaInformada(SENHA));
        terminal.digitar(SENHA);

        assertThatThrownBy(() -> comandoTratar().executar(Argumentos.de("tratar-achado", "--usuario=consulta.local",
                "--achado=" + ACHADO_ID, "--decisao=ACEITO", "--justificativa=Justificativa ficticia")))
                .isInstanceOf(UsoInvalido.class)
                .hasMessageContaining("somente leitura");
        assertThat(gravadas).isEmpty();
    }

    private ComandoCriarAdministrador comandoCriar() {
        return new ComandoCriarAdministrador(usuarios, terminal, saida);
    }

    private ComandoTratarAchado comandoTratar() {
        ConsultaDeAchados consulta = new ConsultaDeAchados() {
            @Override
            public List<AchadoRegistrado> listar(br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados filtro) {
                return List.of(registrado());
            }

            @Override
            public long contar(br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados filtro) {
                return 1;
            }

            @Override
            public Optional<AchadoRegistrado> porId(UUID id) {
                return ACHADO_ID.equals(id) ? Optional.of(registrado()) : Optional.empty();
            }
        };
        HistoricoDeTratativas historico = new HistoricoDeTratativas() {
            @Override
            public RegistroDeTratativa gravar(Tratativa tratativa, UUID autorId) {
                gravadas.add(tratativa);
                return new RegistroDeTratativa(UUID.randomUUID(), tratativa,
                        Optional.of(new AutorDaTratativa(autorId, "fiscal.local", "Fiscal Ficticio", true)),
                        Optional.empty());
            }

            @Override
            public List<RegistroDeTratativa> historico(ChaveDeTratativa chave) {
                return List.of();
            }
        };
        return new ComandoTratarAchado(
                new ServicoDeTratativaAtribuida(consulta, historico, repositorio,
                        Clock.fixed(Instant.parse("1900-01-02T00:00:00Z"), ZoneOffset.UTC)),
                usuarios, terminal, saida);
    }

    private static AchadoRegistrado registrado() {
        Achado achado = new Achado("RXX", "0.0.0-ficticia", Severidade.GRAVE,
                new ChaveAcesso("1".repeat(44)), OptionalInt.of(1),
                List.of(new Evidencia("campoFicticio", Optional.of("99,99"), Optional.empty(),
                        new OrigemEvidencia.DaRegra("derivação fictícia"))),
                "FUNDAMENTO FICTICIO", PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1)),
                ValorEmRisco.naoCalculavel("motivo fictício"));
        return new AchadoRegistrado(ACHADO_ID, achado, new HashDoItem("e".repeat(64)), Optional.empty(),
                Instant.parse("1900-01-01T00:00:00Z"), Instant.parse("1900-01-01T00:00:00Z"));
    }

    /** Terminal falso: devolve o que o teste mandou digitar, na ordem; sem nada, faz de conta que não há terminal. */
    private static final class TerminalFalso implements LeitorDeSenha {

        private final Deque<String> digitadas = new ArrayDeque<>();

        void digitar(String... senhas) {
            digitadas.addAll(List.of(senhas));
        }

        @Override
        public Optional<String> pedir(String pergunta) {
            return Optional.ofNullable(digitadas.poll());
        }
    }

    private static final class CodificadorFalso implements CodificadorDeSenha {

        @Override
        public HashDeSenha codificar(SenhaInformada senha) {
            return new HashDeSenha("falso:" + senha.valor());
        }

        @Override
        public boolean confere(SenhaInformada senha, HashDeSenha hash) {
            return hash.valor().equals("falso:" + senha.valor());
        }
    }

    private static final class UsuariosEmMemoria implements RepositorioDeUsuarios {

        private final Map<UUID, Usuario> usuarios = new LinkedHashMap<>();
        private final Map<UUID, HashDeSenha> hashes = new LinkedHashMap<>();

        @Override
        public Optional<Usuario> porId(UUID id) {
            return Optional.ofNullable(usuarios.get(id));
        }

        @Override
        public Optional<Usuario> porLogin(String login) {
            return usuarios.values().stream().filter(usuario -> usuario.login().equals(login)).findFirst();
        }

        @Override
        public List<Usuario> todos() {
            return List.copyOf(usuarios.values());
        }

        @Override
        public Optional<HashDeSenha> hashDe(UUID id) {
            return Optional.ofNullable(hashes.get(id));
        }

        @Override
        public void inserir(Usuario usuario, HashDeSenha hash) {
            usuarios.put(usuario.id(), usuario);
            hashes.put(usuario.id(), hash);
        }

        @Override
        public void atualizar(Usuario usuario) {
            usuarios.put(usuario.id(), usuario);
        }

        @Override
        public void trocarHash(UUID id, HashDeSenha hash) {
            hashes.put(id, hash);
        }

        @Override
        public void remover(UUID id) {
            usuarios.remove(id);
        }

        @Override
        public long administradoresAtivos() {
            return usuarios.values().stream().filter(Usuario::ehAdministradorAtivo).count();
        }

        @Override
        public boolean temRegistroAtribuido(UUID id) {
            return false;
        }

        @Override
        public <T> T comUsuariosTravados(Supplier<T> operacao) {
            return operacao.get();
        }
    }
}
