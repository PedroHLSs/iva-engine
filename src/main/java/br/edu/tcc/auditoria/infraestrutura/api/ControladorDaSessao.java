package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.infraestrutura.seguranca.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Controlador de /api/sessao: entrar, sair, saber quem está logado, trocar a própria senha e pegar o token contra falsificação de pedido. A senha só aparece no corpo do pedido de entrada, e nunca volta em resposta nenhuma. Acrescentado na Etapa 12.
@RestController
@RequestMapping("/api/sessao")
class ControladorDaSessao {

    private final AuthenticationManager autenticacao;
    private final SecurityContextRepository contextos;
    private final ServicoDeUsuarios usuarios;

    // Construtor que recebe quem confere login e senha, onde a sessão guarda o login e o serviço de usuários.
    ControladorDaSessao(
            AuthenticationManager autenticacao,
            SecurityContextRepository contextos,
            ServicoDeUsuarios usuarios) {
        this.autenticacao = autenticacao;
        this.contextos = contextos;
        this.usuarios = usuarios;
    }

    // Representa o pedido de entrada. O toString nunca mostra a senha.
    record PedidoDeEntrada(String login, String senha) {

        @Override
        public String toString() {
            return "PedidoDeEntrada[login=" + login + ", senha=omitida]";
        }
    }

    // Representa o pedido de troca da própria senha. O toString nunca mostra as senhas.
    record PedidoDeTrocaDeSenha(String senhaAtual, String senhaNova) {

        @Override
        public String toString() {
            return "PedidoDeTrocaDeSenha[omitido]";
        }
    }

    // Representa o token contra falsificação e o cabeçalho em que ele deve voltar.
    record TokenExposto(String cabecalho, String token) {
    }

    // Entrega o token que todo pedido de escrita precisa mandar de volta no cabeçalho.
    @GetMapping("/csrf")
    TokenExposto token(CsrfToken token) {
        return new TokenExposto(token.getHeaderName(), token.getToken());
    }

    // Confere login e senha, abre a sessão e troca o identificador dela, para um identificador obtido antes do login não servir depois.
    @PostMapping
    ResponseEntity<Object> entrar(
            @RequestBody(required = false) PedidoDeEntrada pedido,
            HttpServletRequest requisicao,
            HttpServletResponse resposta) {

        if (pedido == null || pedido.login() == null || pedido.senha() == null) {
            return recusaDeEntrada();
        }
        Authentication autenticado;
        try {
            autenticado = autenticacao.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(pedido.login(), pedido.senha()));
        } catch (AuthenticationException recusada) {
            return recusaDeEntrada();
        }

        requisicao.getSession(true);
        requisicao.changeSessionId();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, requisicao, resposta);

        UsuarioAutenticado quem = (UsuarioAutenticado) autenticado.getPrincipal();
        return ResponseEntity.ok(UsuarioExposto.de(usuarios.buscar(quem.id())));
    }

    // Devolve quem está logado.
    @GetMapping
    UsuarioExposto quemSouEu(@AuthenticationPrincipal UsuarioAutenticado quem) {
        return UsuarioExposto.de(usuarios.buscar(quem.id()));
    }

    // Encerra a sessão.
    @DeleteMapping
    ResponseEntity<Void> sair(HttpServletRequest requisicao) {
        SecurityContextHolder.clearContext();
        HttpSession sessao = requisicao.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    // Troca a própria senha, exigindo a atual.
    @PutMapping("/senha")
    ResponseEntity<Void> trocarSenha(
            @AuthenticationPrincipal UsuarioAutenticado quem,
            @RequestBody(required = false) PedidoDeTrocaDeSenha pedido) {
        if (pedido == null || pedido.senhaAtual() == null || pedido.senhaNova() == null) {
            throw new PedidoInvalido("Informe a senha atual e a senha nova.");
        }
        usuarios.trocarPropriaSenha(
                quem.id(), new SenhaInformada(pedido.senhaAtual()), new SenhaInformada(pedido.senhaNova()));
        return ResponseEntity.noContent().build();
    }

    // Método auxiliar que monta a recusa única de entrada, igual para login inexistente, senha errada e usuário desativado.
    private static ResponseEntity<Object> recusaDeEntrada() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroExposto("ENTRADA_RECUSADA", "Login ou senha inválidos."));
    }
}
