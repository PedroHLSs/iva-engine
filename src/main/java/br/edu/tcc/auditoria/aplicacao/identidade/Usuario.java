package br.edu.tcc.auditoria.aplicacao.identidade;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

// Representa uma pessoa que usa o sistema, com login, nome, perfil e se está ativa. Não carrega a senha nem o hash dela: quem lista usuários não tem por que tocar nisso.
public record Usuario(
        UUID id,
        String login,
        String nome,
        Perfil perfil,
        boolean ativo,
        Instant criadoEm,
        Optional<Instant> desativadoEm) {

    // Login em minúsculas, com letras, números, ponto, hífen e sublinhado.
    private static final Pattern FORMATO_DO_LOGIN = Pattern.compile("^[a-z0-9._-]{3,40}$");

    // Valida o usuário: login no formato, nome preenchido, perfil, datas e desativação coerente com o ativo.
    public Usuario {
        if (id == null) {
            throw new IdentidadeInvalida("O usuário precisa de identificador.");
        }
        login = normalizarLogin(login);
        if (nome == null || nome.isBlank()) {
            throw new IdentidadeInvalida("O usuário precisa de nome.");
        }
        nome = nome.strip();
        if (perfil == null) {
            throw new IdentidadeInvalida("O usuário precisa de perfil.");
        }
        if (criadoEm == null) {
            throw new IdentidadeInvalida("O usuário precisa dizer quando foi criado.");
        }
        if (desativadoEm == null) {
            throw new IdentidadeInvalida(
                    "Usuário nunca desativado se representa com Optional.empty(), nunca com nulo.");
        }
        if (ativo == desativadoEm.isPresent()) {
            throw new IdentidadeInvalida(
                    "Usuário ativo não tem data de desativação, e usuário desativado precisa ter.");
        }
    }

    // Método estático que confere e padroniza o login: minúsculas, sem espaço nas pontas.
    public static String normalizarLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IdentidadeInvalida("O login não foi informado.");
        }
        String normalizado = login.strip().toLowerCase(Locale.ROOT);
        if (!FORMATO_DO_LOGIN.matcher(normalizado).matches()) {
            throw new IdentidadeInvalida(
                    ("O login \"%s\" não serve. Use de 3 a 40 caracteres entre letras sem acento, "
                            + "números, ponto, hífen e sublinhado.").formatted(login.strip()));
        }
        return normalizado;
    }

    // Diz se é administrador ativo, que é o que conta para a regra do último administrador.
    public boolean ehAdministradorAtivo() {
        return ativo && perfil == Perfil.ADMINISTRADOR;
    }

    // Devolve uma cópia com outro nome.
    public Usuario comNome(String novoNome) {
        return new Usuario(id, login, novoNome, perfil, ativo, criadoEm, desativadoEm);
    }

    // Devolve uma cópia com outro perfil.
    public Usuario comPerfil(Perfil novoPerfil) {
        return new Usuario(id, login, nome, novoPerfil, ativo, criadoEm, desativadoEm);
    }

    // Devolve uma cópia desativada na data informada; se já estava desativado, mantém a data antiga.
    public Usuario desativado(Instant quando) {
        if (!ativo) {
            return this;
        }
        return new Usuario(id, login, nome, perfil, false, criadoEm, Optional.of(quando));
    }

    // Devolve uma cópia ativa.
    public Usuario reativado() {
        return new Usuario(id, login, nome, perfil, true, criadoEm, Optional.empty());
    }
}
