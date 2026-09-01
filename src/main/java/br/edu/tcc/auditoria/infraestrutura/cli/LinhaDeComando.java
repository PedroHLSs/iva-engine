package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaInvalida;
import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;
import br.edu.tcc.auditoria.infraestrutura.catalogo.ImportacaoDeCatalogoInvalida;
import br.edu.tcc.auditoria.infraestrutura.lote.OrigemDeLoteInexistente;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface de uso do sistema: uma linha de comando.
 *
 * <p>Não há API REST, e a ausência é deliberada (D006). O sistema é operado por
 * quem audita, em lote, sobre arquivos que já estão na máquina — não há segundo
 * sistema chamando, não há sessão, não há concorrência entre usuários. Uma API
 * traria autenticação, autorização, versionamento de contrato e superfície de
 * exposição de documento fiscal real, tudo isso sem nenhum consumidor.</p>
 *
 * <h2>Erro de uso não vira rastro de pilha</h2>
 *
 * <p>Caminho errado, opção desconhecida ou catálogo malformado imprimem a
 * mensagem e o modo de usar, e devolvem código de saída diferente de zero. Só
 * falha inesperada sobe com a pilha inteira, porque aí a pilha é a informação
 * útil.</p>
 */
@Component
class LinhaDeComando implements CommandLineRunner, ExitCodeGenerator {

    private static final int SUCESSO = 0;
    private static final int ERRO_DE_USO = 2;

    private final Map<String, Comando> comandos = new LinkedHashMap<>();
    private final Saida saida;
    private int codigoDeSaida = SUCESSO;

    LinhaDeComando(List<Comando> comandos, Saida saida) {
        this.saida = saida;
        comandos.stream()
                .sorted((um, outro) -> um.nome().compareTo(outro.nome()))
                .forEach(comando -> this.comandos.put(comando.nome(), comando));
    }

    @Override
    public void run(String... argumentos) {
        if (argumentos.length == 0) {
            imprimirModoDeUsarGeral();
            codigoDeSaida = ERRO_DE_USO;
            return;
        }

        try {
            Argumentos interpretados = Argumentos.de(argumentos);
            Comando comando = comandos.get(interpretados.comando());
            if (comando == null) {
                saida.linha("Comando desconhecido: \"%s\".", interpretados.comando());
                saida.linhaEmBranco();
                imprimirModoDeUsarGeral();
                codigoDeSaida = ERRO_DE_USO;
                return;
            }
            comando.executar(interpretados);

        } catch (UsoInvalido erroDeUso) {
            saida.linha(erroDeUso.getMessage());
            imprimirModoDeUsarDe(argumentos[0]);
            codigoDeSaida = ERRO_DE_USO;

        } catch (OrigemDeLoteInexistente
                 | ImportacaoDeCatalogoInvalida
                 | ConsultaInvalida
                 | ExcecaoDeDominio recusa) {
            // Recusas com mensagem própria e explicativa: o texto basta, e a pilha
            // só esconderia a explicação no meio de trinta linhas de framework.
            saida.linha(recusa.getMessage());
            codigoDeSaida = ERRO_DE_USO;
        }
    }

    @Override
    public int getExitCode() {
        return codigoDeSaida;
    }

    private void imprimirModoDeUsarGeral() {
        saida.linha("Auditoria de coerência de IBS/CBS em documentos fiscais eletrônicos.");
        saida.linhaEmBranco();
        saida.linha("Comandos:");
        comandos.values().forEach(comando ->
                saida.linha("  %-18s %s", comando.nome(), comando.descricao()));
        saida.linhaEmBranco();
        saida.linha("Para o modo de usar de um comando, chame-o sem as opções obrigatórias.");
    }

    private void imprimirModoDeUsarDe(String nomeDoComando) {
        Comando comando = comandos.get(nomeDoComando);
        if (comando == null) {
            return;
        }
        saida.linhaEmBranco();
        saida.linha(comando.modoDeUsar());
    }
}
