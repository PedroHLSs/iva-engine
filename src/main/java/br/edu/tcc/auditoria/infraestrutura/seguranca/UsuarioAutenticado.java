package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

// Representa quem está logado, como a sessão guarda: identificador, login, nome e perfil. Não guarda senha nem hash.
public record UsuarioAutenticado(UUID id, String login, String nome, Perfil perfil) implements Serializable {

    // Prefixo que o Spring Security espera no nome do papel.
    static final String PREFIXO_DO_PAPEL = "ROLE_";

    // Método estático que monta o usuário da sessão a partir do usuário gravado.
    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.id(), usuario.login(), usuario.nome(), usuario.perfil());
    }

    // Devolve o papel do perfil, no formato que a matriz de permissão confere.
    public List<GrantedAuthority> papeis() {
        return List.of(new SimpleGrantedAuthority(PREFIXO_DO_PAPEL + perfil.name()));
    }
}
