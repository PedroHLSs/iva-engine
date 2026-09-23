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

// Classe que roda o comando pedido na linha de comando e define o código de saída. Erro de uso ou recusa com mensagem própria mostra só a mensagem e sai com código 2; só falha inesperada sobe com a pilha inteira.
@Component
class LinhaDeComando implements CommandLineRunner, ExitCodeGenerator {

    private static final int SUCESSO = 0;
    private static final int ERRO_DE_USO = 2;

    private final Map<String, Comando> comandos = new LinkedHashMap<>();
    private final Saida saida;
    private int codigoDeSaida = SUCESSO;

    // Construtor que recebe todos os comandos, ordenados pelo nome, e a saída.
    LinhaDeComando(List<Comando> comandos, Saida saida) {
        this.saida = saida;
        comandos.stream()
                .sorted((um, outro) -> um.nome().compareTo(outro.nome()))
                .forEach(comando -> this.comandos.put(comando.nome(), comando));
    }

    // Interpreta os argumentos e executa o comando; sem argumento ou com comando desconhecido, mostra a lista de comandos.
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
            // Recusas com mensagem própria: o texto basta, e a pilha só esconderia a explicação.
            saida.linha(recusa.getMessage());
            codigoDeSaida = ERRO_DE_USO;
        }
    }

    // Retorna o código de saída do último comando.
    @Override
    public int getExitCode() {
        return codigoDeSaida;
    }

    // Método auxiliar que mostra a lista de todos os comandos.
    private void imprimirModoDeUsarGeral() {
        saida.linha("Auditoria de coerência de IBS/CBS em documentos fiscais eletrônicos.");
        saida.linhaEmBranco();
        saida.linha("Comandos:");
        comandos.values().forEach(comando ->
                saida.linha("  %-18s %s", comando.nome(), comando.descricao()));
        saida.linhaEmBranco();
        saida.linha("Para o modo de usar de um comando, chame-o sem as opções obrigatórias.");
    }

    // Método auxiliar que mostra o modo de usar de um comando.
    private void imprimirModoDeUsarDe(String nomeDoComando) {
        Comando comando = comandos.get(nomeDoComando);
        if (comando == null) {
            return;
        }
        saida.linhaEmBranco();
        saida.linha(comando.modoDeUsar());
    }
}
