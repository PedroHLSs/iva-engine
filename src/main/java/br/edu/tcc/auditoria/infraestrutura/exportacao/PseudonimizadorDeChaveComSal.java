package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.infraestrutura.xml.Pseudonimizador;

import org.springframework.stereotype.Component;

/**
 * Troca a chave de acesso pelo resumo dela, com o mesmo sal de instalação usado
 * para emitente e destinatário.
 *
 * <p>Reusar o pseudonimizador da leitura de XML é deliberado: o sal é da
 * instalação, não do formato de entrada. Dois pseudonimizadores com sais
 * diferentes fariam a mesma nota aparecer com dois nomes conforme o caminho que
 * a produziu, e cruzar duas planilhas do mesmo acervo deixaria de funcionar.</p>
 *
 * <p>Sem sal configurado o contexto nem sobe — ver {@code ConfiguracaoDaAuditoria}.
 * É intencional: sal com valor padrão tornaria o pseudônimo reversível por força
 * bruta, já que o conjunto de chaves possíveis de um emitente conhecido é
 * pequeno.</p>
 */
@Component
class PseudonimizadorDeChaveComSal implements PseudonimizadorDeChave {

    private final Pseudonimizador pseudonimizador;

    PseudonimizadorDeChaveComSal(Pseudonimizador pseudonimizador) {
        this.pseudonimizador = pseudonimizador;
    }

    @Override
    public IdentificadorPseudonimizado de(ChaveAcesso chaveAcesso) {
        if (chaveAcesso == null) {
            throw new IllegalArgumentException("Não há chave de acesso a pseudonimizar.");
        }
        return pseudonimizador.pseudonimizar(chaveAcesso.valor());
    }
}
