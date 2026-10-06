package br.edu.tcc.auditoria.aplicacao.tratativa;

import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import java.util.List;
import java.util.UUID;

// Interface responsável por gravar a tratativa junto com quem a registrou, e por devolver o histórico de uma chave. O histórico só recebe acréscimo: tratar de novo muda a decisão que vale, mas não apaga quem decidiu antes.
public interface HistoricoDeTratativas {

    // Grava a decisão que passa a valer e acrescenta a linha no histórico com o autor, numa transação só.
    RegistroDeTratativa gravar(Tratativa tratativa, UUID autorId);

    // Devolve o histórico da chave, do registro mais antigo ao mais recente.
    List<RegistroDeTratativa> historico(ChaveDeTratativa chave);
}
