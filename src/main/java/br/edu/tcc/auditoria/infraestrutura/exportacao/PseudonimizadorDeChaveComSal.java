package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.infraestrutura.xml.Pseudonimizador;

import org.springframework.stereotype.Component;

// Classe que troca a chave de acesso pelo pseudônimo dela, com o mesmo sal usado para emitente e destinatário, para a mesma nota ter o mesmo nome em qualquer planilha.
@Component
class PseudonimizadorDeChaveComSal implements PseudonimizadorDeChave {

    private final Pseudonimizador pseudonimizador;

    // Construtor que recebe o pseudonimizador da leitura de XML.
    PseudonimizadorDeChaveComSal(Pseudonimizador pseudonimizador) {
        this.pseudonimizador = pseudonimizador;
    }

    // Devolve o pseudônimo da chave de acesso.
    @Override
    public IdentificadorPseudonimizado de(ChaveAcesso chaveAcesso) {
        if (chaveAcesso == null) {
            throw new IllegalArgumentException("Não há chave de acesso a pseudonimizar.");
        }
        return pseudonimizador.pseudonimizar(chaveAcesso.valor());
    }
}
