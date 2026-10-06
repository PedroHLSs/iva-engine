import { el } from '../dom.js';
import { inteiro } from '../formato.js';
import { botaoDeInformacao } from '../painel.js';

const MARCA = {
  POSSIVEL_DIVERGENCIA: '▲',
  REQUER_CONFERENCIA: '◆',
  NAO_FOI_POSSIVEL_CONCLUIR: '■',
  SEM_DIVERGENCIA_IDENTIFICADA: '●',
};

const COR = {
  POSSIVEL_DIVERGENCIA: 'est-divergencia',
  REQUER_CONFERENCIA: 'est-conferencia',
  NAO_FOI_POSSIVEL_CONCLUIR: 'est-inconcluso',
  SEM_DIVERGENCIA_IDENTIFICADA: 'est-semdivergencia',
};

export function selo(estado, rotulo, explicacao) {
  return el('span', {
    classe: 'selo-estado ' + (COR[estado] || 'est-neutro'),
    title: explicacao || null,
  }, [
    el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[estado] || '○' }),
    el('span', { texto: rotulo || estado }),
  ]);
}

export function quadroDeEstados(contagens, titulo, opcoes = {}) {
  const compacto = Boolean(opcoes.compacto);
  const celulas = (contagens || []).map((contagem) => el('div', {
    classe: 'estado-celula ' + (COR[contagem.estado] || 'est-neutro'),
  }, [
    el('div', { classe: 'estado-numero', texto: inteiro(contagem.quantidade) }),
    el('div', { classe: 'estado-rotulo' }, [
      el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[contagem.estado] || '○' }),
      el('span', { texto: contagem.rotulo }),
    ]),
    compacto ? null : el('p', { classe: 'estado-explicacao', texto: contagem.explicacao }),
  ]));

  if (!compacto) {
    return el('section', { classe: 'bloco' }, [
      titulo ? el('h2', { texto: titulo }) : null,
      el('div', { classe: 'estados' }, celulas),
    ]);
  }

  const info = botaoDeInformacao('O que cada situacao quer dizer', [
    el('dl', { classe: 'definicoes-dos-estados' }, (contagens || []).flatMap((contagem) => [
      el('dt', { texto: contagem.rotulo }),
      el('dd', { texto: contagem.explicacao }),
    ])),
    opcoes.nota ? el('p', { texto: opcoes.nota }) : null,
  ].filter(Boolean));
  return el('section', { classe: 'bloco quadro-compacto' }, [
    el('div', { classe: 'titulo-de-secao-linha' }, [
      titulo ? el('h2', { texto: titulo }) : null,
      info.botao,
    ]),
    info.balao,
    el('div', { classe: 'estados' }, celulas),
  ]);
}

export function produtosComPendencia(quantidade, total) {
  return el('p', { classe: 'linha-pendencia' }, [
    el('strong', { texto: inteiro(quantidade) }),
    ' de ' + inteiro(total) + ' produto(s) tem ao menos uma verificacao sem conclusao, '
      + 'inclusive entre os que aparecem com outra situacao acima.',
  ]);
}

export function faixaDeNatureza(natureza) {
  if (!natureza) {
    return null;
  }
  const tabelas = natureza.tabelasFicticias || [];
  return el('div', {
    classe: 'faixa-natureza' + (natureza.exigeAviso ? ' avisa' : ''),
  }, [
    el('strong', { texto: natureza.rotulo }),
    el('p', { texto: natureza.explicacao }),
    tabelas.length
      ? el('p', { classe: 'nota', texto: 'Tabelas de demonstracao: ' + tabelas.join(', ') + '.' })
      : null,
    (natureza.tabelasSemNaturezaDeclarada || []).length
      ? el('p', { classe: 'nota', texto: 'Tabelas sem natureza declarada: '
        + natureza.tabelasSemNaturezaDeclarada.join(', ') + '.' })
      : null,
    el('p', { classe: 'nota', texto: 'Carga de catalogo: ' + natureza.versaoDoCatalogo }),
  ]);
}

export function procedenciaCompacta(natureza) {
  if (!natureza) {
    return null;
  }
  const tabelas = natureza.tabelasFicticias || [];
  const info = botaoDeInformacao('Sobre a carga de catalogo', [
    el('p', { texto: natureza.explicacao }),
    el('p', { texto: 'Carga de catalogo: ' + natureza.versaoDoCatalogo }),
  ]);
  return el('div', { classe: 'procedencia-compacta' + (natureza.exigeAviso ? ' avisa' : '') }, [
    el('div', { classe: 'procedencia-linha' }, [
      el('strong', { texto: natureza.rotulo }),
      el('span', { classe: 'procedencia-versao', texto: natureza.versaoDoCatalogo }),
      info.botao,
    ]),
    natureza.exigeAviso && tabelas.length
      ? el('p', { classe: 'procedencia-tabelas', texto: 'Tabelas de demonstracao: ' + tabelas.join(', ') + '.' })
      : null,
    (natureza.tabelasSemNaturezaDeclarada || []).length
      ? el('p', { classe: 'procedencia-tabelas', texto: 'Tabelas sem natureza declarada: '
        + natureza.tabelasSemNaturezaDeclarada.join(', ') + '.' })
      : null,
    info.balao,
  ]);
}

export function avisoDeUso(texto) {
  return el('p', { classe: 'aviso-de-uso', texto: texto || '' });
}

export function blocoDaLeitura(leitura) {
  const registrada = leituraRegistrada(leitura);
  const ilegiveis = registrada ? leitura.arquivosQueNaoForamLidos : [];
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Leitura' }),
    el('dl', { classe: 'identificacao' }, [
      el('dt', { texto: 'documentos lidos' }),
      el('dd', { texto: inteiro(leitura.documentosLidos) }),
      el('dt', { texto: 'itens lidos' }),
      el('dd', { texto: inteiro(leitura.itensLidos) }),
      el('dt', { texto: 'arquivos que nao puderam ser lidos' }),
      registrada
        ? el('dd', { classe: leitura.arquivosIlegiveis > 0 ? 'destaque-ausencia' : null,
          texto: inteiro(leitura.arquivosIlegiveis) })
        : el('dd', {}, [valorOuMotivo(null, leitura.motivoDosArquivosIlegiveisAusentes)]),
      el('dt', { texto: 'documentos repetidos descartados' }),
      el('dd', {}, [valorOuMotivo(
        leitura.documentosRepetidosDescartados === null || leitura.documentosRepetidosDescartados === undefined
          ? null : inteiro(leitura.documentosRepetidosDescartados),
        leitura.motivoDosRepetidosAusentes)]),
    ]),
    el('p', { classe: 'nota', texto: leitura.comoFoiALeitura }),
    ilegiveis.length
      ? el('ul', { classe: 'lista-ilegiveis' }, ilegiveis.map((arquivo) => el('li', {}, [
        el('span', { classe: 'mono', texto: arquivo.origem }),
        el('p', { classe: 'nota', texto: arquivo.motivo }),
      ])))
      : null,
  ]);
}

export function leituraRegistrada(leitura) {
  return leitura.arquivosIlegiveis !== null && leitura.arquivosIlegiveis !== undefined;
}

export function valorOuMotivo(valor, motivo) {
  if (valor !== null && valor !== undefined && valor !== '') {
    return el('span', { texto: String(valor) });
  }
  return el('span', {
    classe: 'ausente',
    texto: motivo || 'ausente, e a resposta nao disse por que',
  });
}

export function leituraDoCatalogo(leitura, comoDesenhar) {
  if (!leitura) {
    return el('p', { classe: 'ausente', texto: 'a resposta nao trouxe este bloco' });
  }
  const encontrado = leitura.encontrado || [];
  if (encontrado.length === 0) {
    return el('p', { classe: 'ausente', texto: leitura.motivoDaAusencia });
  }
  return comoDesenhar(encontrado);
}

export function falha(erro) {
  const partes = [
    el('strong', { texto: 'Nao foi possivel continuar. ' }),
    erro.message || String(erro),
  ];
  if (erro.detalhe) {
    partes.push(el('pre', { texto: erro.detalhe }));
  }
  return el('div', { classe: 'aviso erro' }, partes);
}

export function cabecalhoDoResultado(titulo, subtitulo, natureza) {
  return el('header', { classe: 'cabecalho-resultado' }, [
    el('h1', { texto: titulo }),
    subtitulo ? el('p', { classe: 'sub', texto: subtitulo }) : null,
    faixaDeNatureza(natureza),
  ]);
}
