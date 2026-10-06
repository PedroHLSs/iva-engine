package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.identidade.AlteracaoDeUsuario;
import br.edu.tcc.auditoria.aplicacao.identidade.Perfil;
import br.edu.tcc.auditoria.aplicacao.identidade.ResultadoDaExclusao;
import br.edu.tcc.auditoria.aplicacao.identidade.SenhaInformada;
import br.edu.tcc.auditoria.aplicacao.identidade.ServicoDeUsuarios;
import br.edu.tcc.auditoria.aplicacao.identidade.Usuario;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Controlador de /api/usuarios: criar, listar, alterar e excluir usuários. Só o administrador chega aqui, e quem recusa os outros é o filtro de segurança, antes deste código. As regras do último administrador e da desativação no lugar da exclusão estão no ServicoDeUsuarios. Acrescentado na Etapa 12.
@RestController
@RequestMapping("/api/usuarios")
class ControladorDeUsuarios {

    private final ServicoDeUsuarios usuarios;

    // Construtor que recebe o serviço de usuários.
    ControladorDeUsuarios(ServicoDeUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    // Representa o pedido de criação. O toString nunca mostra a senha.
    record PedidoDeUsuario(String login, String nome, String perfil, String senha) {

        @Override
        public String toString() {
            return "PedidoDeUsuario[login=" + login + ", perfil=" + perfil + ", senha=omitida]";
        }
    }

    // Representa o pedido de alteração; campo nulo quer dizer "não mexer". O toString nunca mostra a senha.
    record PedidoDeAlteracao(String nome, String perfil, Boolean ativo, String novaSenha) {

        @Override
        public String toString() {
            return "PedidoDeAlteracao[nome=" + nome + ", perfil=" + perfil + ", ativo=" + ativo
                    + ", novaSenha=" + (novaSenha == null ? "não muda" : "omitida") + "]";
        }
    }

    // Representa o que a exclusão fez: remover, ou só desativar quem já registrou tratativa.
    record ResultadoDaExclusaoExposto(String resultado, String explicacao, UsuarioExposto usuario) {
    }

    // Lista todos os usuários, ativos e desativados.
    @GetMapping
    List<UsuarioExposto> listar() {
        return usuarios.listar().stream().map(UsuarioExposto::de).toList();
    }

    // Devolve um usuário.
    @GetMapping("/{id}")
    UsuarioExposto buscar(@PathVariable UUID id) {
        return UsuarioExposto.de(usuarios.buscar(id));
    }

    // Cria um usuário ativo.
    @PostMapping
    ResponseEntity<UsuarioExposto> criar(@RequestBody(required = false) PedidoDeUsuario pedido) {
        if (pedido == null || pedido.senha() == null) {
            throw new PedidoInvalido("Informe login, nome, perfil e senha.");
        }
        Usuario criado = usuarios.criar(
                pedido.login(), pedido.nome(), perfil(pedido.perfil()), new SenhaInformada(pedido.senha()));
        return ResponseEntity.created(URI.create("/api/usuarios/" + criado.id()))
                .body(UsuarioExposto.de(criado));
    }

    // Altera nome, perfil, situação ou senha de um usuário.
    @PutMapping("/{id}")
    UsuarioExposto alterar(@PathVariable UUID id, @RequestBody(required = false) PedidoDeAlteracao pedido) {
        if (pedido == null) {
            throw new PedidoInvalido("Informe o que mudar: nome, perfil, ativo ou novaSenha.");
        }
        AlteracaoDeUsuario alteracao = new AlteracaoDeUsuario(
                Optional.ofNullable(pedido.nome()),
                Optional.ofNullable(pedido.perfil()).map(ControladorDeUsuarios::perfil),
                Optional.ofNullable(pedido.ativo()),
                Optional.ofNullable(pedido.novaSenha()).map(SenhaInformada::new));
        return UsuarioExposto.de(usuarios.alterar(id, alteracao));
    }

    // Exclui o usuário; quem já registrou tratativa ou correção é desativado em vez de apagado.
    @DeleteMapping("/{id}")
    ResultadoDaExclusaoExposto excluir(@PathVariable UUID id) {
        ResultadoDaExclusao resultado = usuarios.excluir(id);
        if (resultado == ResultadoDaExclusao.DESATIVADO) {
            return new ResultadoDaExclusaoExposto(resultado.name(),
                    "O usuário já registrou tratativa ou correção de análise, e por isso foi desativado, "
                            + "e não apagado: o registro precisa continuar atribuído a alguém identificável.",
                    UsuarioExposto.de(usuarios.buscar(id)));
        }
        return new ResultadoDaExclusaoExposto(resultado.name(),
                "O usuário não tinha registrado nada e foi apagado.", null);
    }

    // Método auxiliar que converte o texto no perfil; recusa valor desconhecido.
    private static Perfil perfil(String informado) {
        return Parametros.constante(informado, Perfil.class, "perfil", null);
    }
}
