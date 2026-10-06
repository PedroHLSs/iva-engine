package br.edu.tcc.auditoria.aplicacao.identidade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Repositório utilizado para buscar e gravar usuários. O hash da senha fica separado do usuário, e só sai por hashDe.
public interface RepositorioDeUsuarios {

    // Busca o usuário pelo identificador.
    Optional<Usuario> porId(UUID id);

    // Busca o usuário pelo login já padronizado.
    Optional<Usuario> porLogin(String login);

    // Lista todos os usuários, ativos e desativados, ordenados pelo login.
    List<Usuario> todos();

    // Devolve o hash da senha do usuário.
    Optional<HashDeSenha> hashDe(UUID id);

    // Grava um usuário novo com o hash da senha.
    void inserir(Usuario usuario, HashDeSenha hash);

    // Grava nome, perfil e situação de um usuário que já existe.
    void atualizar(Usuario usuario);

    // Troca o hash da senha.
    void trocarHash(UUID id, HashDeSenha hash);

    // Apaga o usuário. Só é chamado para quem não registrou nada.
    void remover(UUID id);

    // Conta os administradores ativos.
    long administradoresAtivos();

    // Diz se o usuário já registrou tratativa ou correção de análise, olhando o histórico e não só a decisão que vale hoje.
    boolean temRegistroAtribuido(UUID id);

    // Roda a operação com os usuários travados, numa transação só, para dois administradores não se rebaixarem ao mesmo tempo e deixarem o sistema sem nenhum.
    <T> T comUsuariosTravados(Supplier<T> operacao);
}
