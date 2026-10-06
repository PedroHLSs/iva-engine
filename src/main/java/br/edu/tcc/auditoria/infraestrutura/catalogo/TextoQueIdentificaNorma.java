package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

// Classe que recusa dispositivo legal ou fonte normativa sem nenhuma letra, como "0" ou "-": um número ou um sinal solto não identifica norma alguma (D022). O critério é só de forma. O sistema não sabe qual norma é a certa (CLAUDE.md, seção 5) e não confere se o texto aceito corresponde a ela; só recusa o que não pode ser norma nenhuma.
final class TextoQueIdentificaNorma {

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private TextoQueIdentificaNorma() {
    }

    // Método estático que devolve o valor lido, ou recusa a linha apontando a coluna e o valor.
    static String exigir(LinhaCsv linha, String coluna, String valor) {
        if (valor.codePoints().noneMatch(Character::isLetter)) {
            throw new RecusaDeCampo(linha.numero(), coluna, linha.valorComoVeio(coluna),
                    ("Linha %d: %s = \"%s\" não identifica norma alguma: não tem nenhuma letra. Dispositivo e "
                            + "fonte dizem de que norma se trata, e um número ou sinal solto não diz. O sistema "
                            + "não preenche nem corrige este campo: informe o texto da origem, ou deixe a linha "
                            + "fora da carga.").formatted(linha.numero(), coluna, valor),
                    null);
        }
        return valor;
    }
}
