/* ---------------------------------------------------------------------------
   Pecas repetidas entre telas: identificacao da execucao, barra de navegacao
   da execucao, painel da planilha e apresentacao de falha.
   --------------------------------------------------------------------------- */

import { el } from './dom.js';
import { dataHora, inteiro } from './formato.js';
import { endereco } from './roteador.js';

/**
 * A identificacao da rodada, inteira e visivel.
 *
 * O hash de entrada sai por extenso, e nao truncado com reticencias: e ele que
 * responde "esta e a mesma entrada de ontem?", e um resumo cortado nao responde
 * isso. Catalogo e conjunto de regras estao ao lado pelo mesmo motivo - um
 * apontamento que hoje nao procede mais, porque a tabela mudou, e
 * indistinguivel de erro do sistema quando o relatorio nao diz contra o que foi
 * produzido.
 */
export function identificacao(execucao) {
  return el('dl', { classe: 'identificacao' }, [
    el('dt', { texto: 'rodou em' }),
    el('dd', { texto: dataHora(execucao.dataHora) }),
    el('dt', { texto: 'catalogo' }),
    el('dd', { texto: execucao.versaoCatalogo }),
    el('dt', { texto: 'conjunto de regras' }),
    el('dd', { texto: execucao.versaoConjuntoRegras }),
    el('dt', { texto: 'hash de entrada' }),
    el('dd', { texto: execucao.hashEntrada }),
    el('dt', { texto: 'acervo' }),
    el('dd', {
      texto: inteiro(execucao.quantidadeDocumentos) + ' documento(s), '
        + inteiro(execucao.quantidadeItens) + ' item(ns)',
    }),
  ]);
}

/** Os atalhos entre as telas de uma mesma execucao. */
export function navegacaoDaExecucao(execucaoId, telaAtual) {
  const atalho = (nome, rotulo) => el('a', {
    classe: 'botao' + (telaAtual === nome ? ' principal' : ''),
    href: endereco(nome, execucaoId),
    texto: rotulo,
  });
  return el('div', { classe: 'acoes' }, [
    atalho('panorama', 'Panorama'),
    atalho('achados', 'Achados'),
    atalho('naoAvaliados', 'Nao avaliados'),
  ]);
}

const NOME_DO_JAR = 'auditoria-ibs-cbs-<versao>.jar';

/**
 * O papel de trabalho, oferecido em toda tela de execucao.
 *
 * NAO e um link de download, e a diferenca e deliberada: a API da Etapa 8 tem
 * quatro GET e nenhum serve arquivo. A planilha e do comando "exportar", na
 * CLI. Um botao que parecesse baixar e devolvesse 404 seria pior que a
 * instrucao honesta, entao aqui vai a invocacao exata, com o identificador
 * desta execucao ja preenchido, pronta para copiar.
 */
export function painelDaPlanilha(execucaoId) {
  const comando = 'java -jar ' + NOME_DO_JAR + ' exportar \\n'
    + '  --arquivo=papel-de-trabalho.xlsx \\n'
    + '  --execucao=' + execucaoId;

  const linhaDeComando = el('pre', { texto: comando });
  const aviso = el('span', { classe: 'nota', texto: '' });

  const copiar = el('button', {
    classe: 'botao',
    texto: 'copiar comando',
    aoClicar: async () => {
      try {
        await navigator.clipboard.writeText(comando);
        aviso.textContent = 'copiado';
      } catch (semAreaDeTransferencia) {
        aviso.textContent = 'nao foi possivel copiar; selecione o texto acima';
      }
    },
  });

  const painel = el('div', { classe: 'painel-comando oculto' }, [
    el('p', {
      texto: 'A planilha continua sendo o artefato que circula, e quem a gera e a CLI: '
        + 'a interface web e somente leitura e nao grava arquivo. Rode isto na pasta do sistema.',
    }),
    linhaDeComando,
    el('div', { classe: 'acoes' }, [copiar, aviso]),
    el('p', {
      classe: 'nota',
      texto: 'Sem --execucao, o comando exporta a rodada mais recente. As tres abas sao Resumo, '
        + 'Achados e Nao avaliados, e nenhum identificador em texto claro sai na planilha.',
    }),
  ]);

  const botao = el('button', {
    classe: 'botao',
    texto: 'planilha (xlsx)',
    aoClicar: () => painel.classList.toggle('oculto'),
  });

  return { botao, painel };
}

/** Uma falha de leitura, dita por inteiro. */
export function falha(erro) {
  const partes = [
    el('strong', { texto: 'Nao foi possivel ler. ' }),
    erro.message || String(erro),
  ];
  if (erro.detalhe) {
    partes.push(el('pre', { texto: erro.detalhe }));
  }
  return el('div', { classe: 'aviso erro' }, partes);
}

/** Aviso simples, para o que a resposta deixou de dizer. */
export function nota(texto) {
  return el('p', { classe: 'nota', texto });
}
