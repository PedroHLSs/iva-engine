import { el } from './dom.js';
import { dataHora, inteiro } from './formato.js';
import { endereco } from './roteador.js';

export function identificacao(execucao) {
  const par = (chave, valor, extras = {}) => el('div', { classe: 'meta-par' }, [
    el('dt', { texto: chave }),
    el('dd', Object.assign({ texto: valor }, extras)),
  ]);
  return el('dl', { classe: 'metadados' }, [
    par('rodou em', dataHora(execucao.dataHora)),
    par('catalogo', execucao.versaoCatalogo),
    par('conjunto de regras', execucao.versaoConjuntoRegras),
    par('tolerancia R05', textoDaTolerancia(execucao.toleranciaDeValor)),
    par('hash de entrada', execucao.hashEntrada, {
      classe: 'hash', title: execucao.hashEntrada, tabindex: 0,
    }),
    par('acervo', inteiro(execucao.quantidadeDocumentos) + ' documento(s), '
      + inteiro(execucao.quantidadeItens) + ' item(ns)'),
  ]);
}

export function textoDaTolerancia(tolerancia) {
  return (tolerancia && tolerancia.texto) || (tolerancia && tolerancia.motivoDaAusencia)
    || 'a resposta nao trouxe a tolerancia';
}

export function linhaDaTolerancia(tolerancia) {
  return el('p', {
    classe: 'sub linha-da-tolerancia',
    texto: 'Tolerancia de valor da R05 nesta execucao: ' + textoDaTolerancia(tolerancia),
  });
}

export function faixaDeNatureza(natureza) {
  if (!natureza) {
    return el('p', { classe: 'faixa-tecnica avisa', texto: 'A resposta nao trouxe a procedencia do catalogo.' });
  }
  const ficticias = natureza.tabelasFicticias || [];
  const semNatureza = natureza.tabelasSemNaturezaDeclarada || [];
  return el('div', { classe: 'faixa-tecnica' + (natureza.exigeAviso ? ' avisa' : ''), role: 'note' }, [
    el('strong', { texto: natureza.rotulo }),
    el('p', { texto: natureza.explicacao }),
    ficticias.length ? el('p', { texto: 'Tabelas ficticias: ' + ficticias.join(', ') + '.' }) : null,
    semNatureza.length
      ? el('p', { texto: 'Tabelas sem natureza declarada: ' + semNatureza.join(', ') + '.' })
      : null,
    el('p', { classe: 'faixa-versao', texto: 'Carga de catalogo: ' + natureza.versaoDoCatalogo }),
  ]);
}

export function navegacaoDaExecucao(execucaoId, telaAtual) {
  const atalho = (nome, rotulo) => el('a', {
    classe: 'aba-da-execucao',
    href: endereco(nome, execucaoId),
    'aria-current': telaAtual === nome ? 'page' : null,
    texto: rotulo,
  });
  return el('nav', { classe: 'abas-da-execucao', 'aria-label': 'Telas desta execucao' }, [
    atalho('panorama', 'Panorama'),
    atalho('achados', 'Achados'),
    atalho('naoAvaliados', 'Nao avaliados'),
  ]);
}

const NOME_DO_JAR = 'auditoria-ibs-cbs-<versao>.jar';

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

export function nota(texto) {
  return el('p', { classe: 'nota', texto });
}
