package br.edu.tcc.auditoria.infraestrutura.seguranca;

import br.edu.tcc.auditoria.aplicacao.identidade.RepositorioDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.Optional;

// Filtro que relê do banco, a cada pedido, o usuário da sessão. Assim, rebaixar ou desativar alguém vale no pedido seguinte, e não só quando a sessão vencer. Usuário apagado ou desativado perde a sessão na hora.
final class RecargaDoUsuario implements Filter {

    private final RepositorioDeUsuarios usuarios;
    private final SecurityContextRepository contextos;
    private final RespostaDeRecusa recusa;

    // Construtor que recebe os usuários, onde a sessão guarda a autenticação e quem escreve a recusa.
    RecargaDoUsuario(
            RepositorioDeUsuarios usuarios, SecurityContextRepository contextos, RespostaDeRecusa recusa) {
        this.usuarios = usuarios;
        this.contextos = contextos;
        this.recusa = recusa;
    }

    // Confere o usuário da sessão e atualiza o perfil se ele mudou; encerra a sessão se o usuário sumiu ou foi desativado.
    @Override
    public void doFilter(ServletRequest pedidoBruto, ServletResponse respostaBruta, FilterChain cadeia)
            throws IOException, ServletException {

        HttpServletRequest pedido = (HttpServletRequest) pedidoBruto;
        HttpServletResponse resposta = (HttpServletResponse) respostaBruta;

        Authentication atual = SecurityContextHolder.getContext().getAuthentication();
        if (atual == null || !(atual.getPrincipal() instanceof UsuarioAutenticado daSessao)) {
            cadeia.doFilter(pedido, resposta);
            return;
        }

        Optional<Usuario> gravado = usuarios.porId(daSessao.id());
        if (gravado.isEmpty() || !gravado.get().ativo()) {
            SecurityContextHolder.clearContext();
            HttpSession sessao = pedido.getSession(false);
            if (sessao != null) {
                sessao.invalidate();
            }
            recusa.escrever(resposta, HttpServletResponse.SC_UNAUTHORIZED, "SESSAO_ENCERRADA",
                    "A sessão foi encerrada: o usuário foi desativado ou excluído.");
            return;
        }

        UsuarioAutenticado recarregado = UsuarioAutenticado.de(gravado.get());
        if (!recarregado.equals(daSessao)) {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    recarregado, null, recarregado.papeis()));
            SecurityContextHolder.setContext(contexto);
            contextos.saveContext(contexto, pedido, resposta);
        }
        cadeia.doFilter(pedido, resposta);
    }
}
