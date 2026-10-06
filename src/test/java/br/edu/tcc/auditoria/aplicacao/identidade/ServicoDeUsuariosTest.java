package br.edu.tcc.auditoria.aplicacao.identidade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As duas regras de usuário que não podem depender da tela: o sistema nunca fica
 * sem administrador ativo, e quem já registrou tratativa é desativado, e não
 * apagado.
 *
 * <p>O repositório e o codificador são falsos, em memória, para a regra ser
 * testada sem banco. O codificador falso não é BCrypt: só prefixa o texto, e
 * nunca sai deste teste.</p>
 */
class ServicoDeUsuariosTest {

    private static final SenhaInformada SENHA = new SenhaInformada("senha-ficticia-0000");

    private RepositorioEmMemoria repositorio;
    private ServicoDeUsuarios servico;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioEmMemoria();
        servico = new ServicoDeUsuarios(repositorio, new CodificadorFalso(),
                Clock.fixed(Instant.parse("1900-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void naoDeveExcluirOUltimoAdministradorAtivo() {
        Usuario unico = servico.criar("admin.unico", "Admin", Perfil.ADMINISTRADOR, SENHA);

        assertThatThrownBy(() -> servico.excluir(unico.id()))
                .isInstanceOf(UltimoAdministrador.class)
                .hasMessageContaining("único administrador ativo");
        assertThat(repositorio.porId(unico.id())).isPresent();
    }

    @Test
    void naoDeveRebaixarOUltimoAdministradorAtivo() {
        Usuario unico = servico.criar("admin.unico", "Admin", Perfil.ADMINISTRADOR, SENHA);

        assertThatThrownBy(() -> servico.alterar(unico.id(), alteracao(Perfil.FISCAL, null)))
                .isInstanceOf(UltimoAdministrador.class);
        assertThat(repositorio.porId(unico.id()).orElseThrow().perfil()).isEqualTo(Perfil.ADMINISTRADOR);
    }

    @Test
    void naoDeveDesativarOUltimoAdministradorAtivo() {
        Usuario unico = servico.criar("admin.unico", "Admin", Perfil.ADMINISTRADOR, SENHA);

        assertThatThrownBy(() -> servico.alterar(unico.id(), alteracao(null, false)))
                .isInstanceOf(UltimoAdministrador.class);
        assertThat(repositorio.porId(unico.id()).orElseThrow().ativo()).isTrue();
    }

    @Test
    void administradorDesativadoNaoContaComoAdministradorQueSobra() {
        Usuario primeiro = servico.criar("admin.um", "Admin um", Perfil.ADMINISTRADOR, SENHA);
        Usuario segundo = servico.criar("admin.dois", "Admin dois", Perfil.ADMINISTRADOR, SENHA);
        servico.alterar(segundo.id(), alteracao(null, false));

        assertThatThrownBy(() -> servico.excluir(primeiro.id()))
                .as("o segundo está desativado, e desativado não administra")
                .isInstanceOf(UltimoAdministrador.class);
    }

    @Test
    void deveRebaixarAdministradorQuandoHaOutroAtivo() {
        Usuario primeiro = servico.criar("admin.um", "Admin um", Perfil.ADMINISTRADOR, SENHA);
        servico.criar("admin.dois", "Admin dois", Perfil.ADMINISTRADOR, SENHA);

        Usuario rebaixado = servico.alterar(primeiro.id(), alteracao(Perfil.CONSULTA, null));

        assertThat(rebaixado.perfil()).isEqualTo(Perfil.CONSULTA);
        assertThat(repositorio.administradoresAtivos()).isEqualTo(1);
    }

    @Test
    void aRegraDoUltimoAdministradorDeveRodarDentroDaTrava() {
        Usuario unico = servico.criar("admin.unico", "Admin", Perfil.ADMINISTRADOR, SENHA);
        repositorio.chamadasForaDaTrava.clear();

        assertThatThrownBy(() -> servico.excluir(unico.id())).isInstanceOf(UltimoAdministrador.class);

        assertThat(repositorio.chamadasForaDaTrava)
                .as("contar administradores fora da trava deixaria dois se rebaixarem ao mesmo tempo")
                .doesNotContain("administradoresAtivos");
    }

    @Test
    void deveDesativarEmVezDeApagarQuemJaRegistrouTratativa() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);
        Usuario fiscal = servico.criar("fiscal.um", "Fiscal", Perfil.FISCAL, SENHA);
        repositorio.comRegistro.add(fiscal.id());

        ResultadoDaExclusao resultado = servico.excluir(fiscal.id());

        assertThat(resultado).isEqualTo(ResultadoDaExclusao.DESATIVADO);
        Usuario gravado = repositorio.porId(fiscal.id()).orElseThrow();
        assertThat(gravado.ativo()).isFalse();
        assertThat(gravado.desativadoEm()).isPresent();
    }

    @Test
    void deveApagarQuemNuncaRegistrouNada() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);
        Usuario consulta = servico.criar("consulta.um", "Consulta", Perfil.CONSULTA, SENHA);

        assertThat(servico.excluir(consulta.id())).isEqualTo(ResultadoDaExclusao.REMOVIDO);
        assertThat(repositorio.porId(consulta.id())).isEmpty();
    }

    @Test
    void naoDeveAutenticarUsuarioDesativado() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);
        Usuario fiscal = servico.criar("fiscal.um", "Fiscal", Perfil.FISCAL, SENHA);
        assertThat(servico.autenticar("fiscal.um", SENHA)).isPresent();

        servico.alterar(fiscal.id(), alteracao(null, false));

        assertThat(servico.autenticar("fiscal.um", SENHA)).isEmpty();
    }

    @Test
    void deveRecusarSenhaErradaELoginInexistenteDoMesmoJeito() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);

        assertThat(servico.autenticar("admin.um", new SenhaInformada("outra-senha-qualquer"))).isEmpty();
        assertThat(servico.autenticar("ninguem", SENHA)).isEmpty();
        assertThat(servico.autenticar("login com espaço", SENHA)).isEmpty();
    }

    @Test
    void deveRecusarSenhaCurta() {
        assertThatThrownBy(() -> servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR,
                new SenhaInformada("curta")))
                .isInstanceOf(IdentidadeInvalida.class)
                .hasMessageContaining(String.valueOf(SenhaInformada.TAMANHO_MINIMO));
    }

    @Test
    void deveRecusarLoginRepetido() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);

        assertThatThrownBy(() -> servico.criar("ADMIN.UM", "Outro", Perfil.FISCAL, SENHA))
                .as("login é comparado já em minúsculas")
                .isInstanceOf(IdentidadeInvalida.class)
                .hasMessageContaining("admin.um");
    }

    @Test
    void aSenhaNaoDeveAparecerNoTextoDeNenhumObjeto() {
        SenhaInformada senha = new SenhaInformada("senha-que-nao-pode-vazar");
        HashDeSenha hash = new CodificadorFalso().codificar(senha);

        assertThat(senha.toString()).doesNotContain("senha-que-nao-pode-vazar");
        assertThat(hash.toString()).doesNotContain(hash.valor());
    }

    @Test
    void criarAdministradorDeveRecuperarLoginExistente() {
        servico.criar("admin.um", "Admin", Perfil.ADMINISTRADOR, SENHA);
        Usuario fiscal = servico.criar("fiscal.um", "Fiscal", Perfil.FISCAL, SENHA);
        servico.alterar(fiscal.id(), alteracao(null, false));
        SenhaInformada nova = new SenhaInformada("senha-nova-ficticia-1111");

        ServicoDeUsuarios.ResultadoDoAdministradorLocal resultado =
                servico.garantirAdministrador("fiscal.um", "Fiscal", nova);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.usuario().perfil()).isEqualTo(Perfil.ADMINISTRADOR);
        assertThat(resultado.usuario().ativo()).isTrue();
        assertThat(servico.autenticar("fiscal.um", nova)).isPresent();
        assertThat(servico.autenticar("fiscal.um", SENHA)).isEmpty();
    }

    private static AlteracaoDeUsuario alteracao(Perfil perfil, Boolean ativo) {
        return new AlteracaoDeUsuario(
                Optional.empty(), Optional.ofNullable(perfil), Optional.ofNullable(ativo), Optional.empty());
    }

    /** Codificador falso: só para o teste não depender de BCrypt. */
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

    /** Repositório em memória que registra quais leituras aconteceram fora da trava. */
    private static final class RepositorioEmMemoria implements RepositorioDeUsuarios {

        private final Map<UUID, Usuario> usuarios = new LinkedHashMap<>();
        private final Map<UUID, HashDeSenha> hashes = new LinkedHashMap<>();
        final Set<UUID> comRegistro = new HashSet<>();
        final List<String> chamadasForaDaTrava = new ArrayList<>();
        private boolean travado;

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
            return usuarios.values().stream().sorted(Comparator.comparing(Usuario::login)).toList();
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
            hashes.remove(id);
        }

        @Override
        public long administradoresAtivos() {
            if (!travado) {
                chamadasForaDaTrava.add("administradoresAtivos");
            }
            return usuarios.values().stream().filter(Usuario::ehAdministradorAtivo).count();
        }

        @Override
        public boolean temRegistroAtribuido(UUID id) {
            return comRegistro.contains(id);
        }

        @Override
        public <T> T comUsuariosTravados(Supplier<T> operacao) {
            travado = true;
            try {
                return operacao.get();
            } finally {
                travado = false;
            }
        }
    }
}
