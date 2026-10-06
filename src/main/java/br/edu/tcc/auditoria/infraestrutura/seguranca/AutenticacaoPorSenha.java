package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.IdentidadeInvalida;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Classe que confere login e senha para o Spring Security, pelo mesmo ServicoDeUsuarios que a linha de comando usa. A recusa é uma só, para não dizer se o login existe.
@Component
class AutenticacaoPorSenha implements AuthenticationProvider {

    // Mensagem única para login inexistente, senha errada e usuário desativado.
    static final String RECUSA = "Login ou senha inválidos.";

    private final ServicoDeUsuarios usuarios;

    // Construtor que recebe o serviço de usuários.
    AutenticacaoPorSenha(ServicoDeUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    // Confere as credenciais e devolve a autenticação sem a senha.
    @Override
    public Authentication authenticate(Authentication pedido) {
        String login = pedido.getName();
        Object credencial = pedido.getCredentials();
        if (!(credencial instanceof String senha) || senha.isEmpty()) {
            throw new BadCredentialsException(RECUSA);
        }

        Optional<Usuario> autenticado;
        try {
            autenticado = usuarios.autenticar(login, new SenhaInformada(senha));
        } catch (IdentidadeInvalida invalida) {
            throw new BadCredentialsException(RECUSA);
        }
        UsuarioAutenticado principal = autenticado.map(UsuarioAutenticado::de)
                .orElseThrow(() -> new BadCredentialsException(RECUSA));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.papeis());
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
