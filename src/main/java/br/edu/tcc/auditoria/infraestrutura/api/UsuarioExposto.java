package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import java.time.Instant;

// Representa um usuário como a API mostra. Não há campo de senha nem de hash, e isso é de propósito: nenhum caminho da API devolve nenhum dos dois. Desativação ausente vem null com o motivo ao lado.
public record UsuarioExposto(
        String id,
        String login,
        String nome,
        String perfil,
        String rotuloDoPerfil,
        boolean ativo,
        Instant criadoEm,
        Instant desativadoEm,
        String motivoDaDesativacaoAusente,
        PermissoesExpostas permissoes) {

    // Motivo escrito quando o usuário nunca foi desativado.
    static final String NUNCA_DESATIVADO = "usuário ativo: nunca foi desativado";

    // Representa o que o perfil pode fazer, para a tela decidir que botão mostrar. Quem recusa de verdade é o servidor.
    public record PermissoesExpostas(boolean enviarNota, boolean registrarTratativa, boolean administrar) {

        // Método estático que monta as permissões do perfil.
        static PermissoesExpostas de(Perfil perfil) {
            return new PermissoesExpostas(
                    perfil.podeEnviarNota(), perfil.podeRegistrarTratativa(), perfil.podeAdministrar());
        }
    }

    // Método estático que monta o usuário exposto a partir do usuário gravado.
    static UsuarioExposto de(Usuario usuario) {
        return new UsuarioExposto(
                usuario.id().toString(),
                usuario.login(),
                usuario.nome(),
                usuario.perfil().name(),
                usuario.perfil().rotulo(),
                usuario.ativo(),
                usuario.criadoEm(),
                usuario.desativadoEm().orElse(null),
                usuario.desativadoEm().isPresent() ? null : NUNCA_DESATIVADO,
                PermissoesExpostas.de(usuario.perfil()));
    }
}
