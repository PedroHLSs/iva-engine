package br.edu.tcc.auditoria.aplicacao.identidade;

// Enum que diz o que aconteceu quando um administrador pediu para excluir um usuário.
public enum ResultadoDaExclusao {

    // O usuário não tinha registrado nada e foi apagado.
    REMOVIDO,

    // O usuário já tinha registrado tratativa ou correção de análise, então foi só desativado, para o registro continuar atribuído a alguém identificável.
    DESATIVADO
}
