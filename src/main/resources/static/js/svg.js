/* ---------------------------------------------------------------------------
   Graficos em SVG escrito a mao. Sem biblioteca, sem CDN.

   DUAS REGRAS QUE ATRAVESSAM TODO GRAFICO DESTE ARQUIVO

   1. NAO_AVALIADO tem faixa propria, hachura propria e rotulo proprio. Ele
      nunca e somado a CONFORME e nunca sai por subtracao. Um lote com 4
      apontamentos e 7 nao avaliados nao pode desenhar como lote quase limpo.

   2. Cor nunca e a unica codificacao. Cada faixa recebe hachura diferente
      (45 graus, 135 graus, pontilhado) e o numero vem escrito ao lado. A figura
      continua legivel impressa em preto e branco e para quem nao distingue as
      cores.

   Valor zero desenha barra de comprimento zero E escreve "0" - o numero e que
   informa. Valor ausente nao desenha barra nenhuma e escreve o motivo: barra de
   altura zero para metrica indefinida seria dizer "zero", que e falso.
   --------------------------------------------------------------------------- */

const NS = 'http://www.w3.org/2000/svg';

let sequencia = 0;

function noSvg(tag, atributos = {}) {
  const no = document.createElementNS(NS, tag);
  for (const [nome, valor] of Object.entries(atributos)) {
    if (valor !== null && valor !== undefined) {
      no.setAttribute(nome, String(valor));
    }
  }
  return no;
}

function textoSvg(x, y, conteudo, atributos = {}) {
  const no = noSvg('text', Object.assign({ x, y }, atributos));
  no.textContent = conteudo;
  return no;
}

/** As tres hachuras, criadas uma vez por figura com identificadores proprios. */
function definirHachuras(svg, prefixo) {
  const defs = noSvg('defs');

  const achado = noSvg('pattern', {
    id: prefixo + '-achado', width: 6, height: 6,
    patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(45)',
  });
  achado.appendChild(noSvg('rect', { width: 6, height: 6, fill: 'var(--achado)' }));
  achado.appendChild(noSvg('line', {
    x1: 0, y1: 0, x2: 0, y2: 6, stroke: '#ffffff', 'stroke-width': 2, opacity: 0.55,
  }));

  const naoAvaliado = noSvg('pattern', {
    id: prefixo + '-naoavaliado', width: 6, height: 6,
    patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(135)',
  });
  naoAvaliado.appendChild(noSvg('rect', { width: 6, height: 6, fill: 'var(--naoavaliado)' }));
  naoAvaliado.appendChild(noSvg('line', {
    x1: 0, y1: 0, x2: 0, y2: 6, stroke: '#ffffff', 'stroke-width': 2, opacity: 0.6,
  }));

  const conforme = noSvg('pattern', {
    id: prefixo + '-conforme', width: 5, height: 5, patternUnits: 'userSpaceOnUse',
  });
  conforme.appendChild(noSvg('rect', { width: 5, height: 5, fill: 'var(--conforme)' }));
  conforme.appendChild(noSvg('circle', { cx: 2.5, cy: 2.5, r: 1, fill: '#ffffff', opacity: 0.6 }));

  defs.appendChild(achado);
  defs.appendChild(naoAvaliado);
  defs.appendChild(conforme);
  svg.appendChild(defs);

  return {
    ACHADO: 'url(#' + prefixo + '-achado)',
    NAO_AVALIADO: 'url(#' + prefixo + '-naoavaliado)',
    CONFORME: 'url(#' + prefixo + '-conforme)',
  };
}

/**
 * Barra empilhada dos tres desfechos da execucao inteira.
 *
 * conforme null significa que a derivacao nao fechou. Nesse caso a faixa de
 * conforme NAO e desenhada e o rotulo diz que nao e derivavel - desenhar zero
 * ali afirmaria que nenhum item esta conforme, que e afirmacao sobre o acervo,
 * e o problema esta no banco.
 */
export function barraDeDesfechos({ achado, naoAvaliado, conforme }) {
  sequencia += 1;
  const prefixo = 'hachura' + sequencia;
  const largura = 900;
  const alturaDaBarra = 34;

  const svg = noSvg('svg', {
    class: 'grafico', viewBox: '0 0 ' + largura + ' 92', role: 'img',
    'aria-label': 'Apontamentos ' + achado + ', nao avaliados ' + naoAvaliado
      + ', conformes ' + (conforme === null ? 'nao derivavel' : conforme),
  });
  const hachura = definirHachuras(svg, prefixo);

  const somaConhecida = achado + naoAvaliado + (conforme === null ? 0 : conforme);
  const total = somaConhecida > 0 ? somaConhecida : 1;

  const faixas = [
    { desfecho: 'ACHADO', rotulo: 'apontamentos', valor: achado },
    { desfecho: 'NAO_AVALIADO', rotulo: 'nao avaliados', valor: naoAvaliado },
    { desfecho: 'CONFORME', rotulo: 'conformes', valor: conforme },
  ];

  let x = 0;
  for (const faixa of faixas) {
    if (faixa.valor === null || faixa.valor === 0) {
      continue;
    }
    const comprimento = Math.max(2, (faixa.valor / total) * largura);
    svg.appendChild(noSvg('rect', {
      x, y: 8, width: comprimento, height: alturaDaBarra,
      fill: hachura[faixa.desfecho], stroke: 'rgba(0,0,0,.14)',
    }));
    x += comprimento;
  }

  svg.appendChild(noSvg('line', {
    x1: 0, y1: 8 + alturaDaBarra, x2: largura, y2: 8 + alturaDaBarra, stroke: 'var(--eixo)',
  }));

  let rotuloX = 0;
  for (const faixa of faixas) {
    const texto = faixa.valor === null
      ? faixa.rotulo + ': nao derivavel'
      : faixa.rotulo + ': ' + faixa.valor.toLocaleString('pt-BR');
    svg.appendChild(textoSvg(rotuloX + 32, 82, texto, {
      'font-size': 12.5, fill: 'var(--tinta2)', 'font-family': 'var(--sans)',
    }));
    svg.appendChild(noSvg('rect', {
      x: rotuloX, y: 72, width: 24, height: 12,
      fill: faixa.valor === null ? 'none' : hachura[faixa.desfecho],
      stroke: 'var(--eixo)',
      'stroke-dasharray': faixa.valor === null ? '2 2' : null,
    }));
    rotuloX += 300;
  }
  return svg;
}

/**
 * Uma linha por regra, com apontamentos e nao avaliados lado a lado.
 *
 * A escala e o maior (achado + naoAvaliado) entre as regras, e nao o total de
 * itens: em escala de acervo as duas categorias que interessam viram um fio de
 * cabelo e a figura deixa de informar. Conforme aparece escrito, na coluna da
 * direita, e nao desenhado - nao esta omitido, esta em outra forma.
 *
 * Regra que nao apontou nada E nao deixou nada por avaliar recebe um traco
 * curto no lugar da barra, e nao o vazio: assim ela se distingue, na figura, da
 * regra que so nao apontou.
 *
 * Depois da Etapa 11 cada linha ganhou uma faixa de texto acima da barra, com o
 * codigo e o nome da regra por extenso. O nome nao cabia a esquerda da barra, e
 * SVG nao quebra linha; sem nome, a faixa escreve o motivo que a resposta trouxe.
 */
export function barrasPorRegra(porRegra) {
  sequencia += 1;
  const prefixo = 'hachura' + sequencia;

  const alturaDaLinha = 46;
  const margemEsquerda = 40;
  const larguraDoGrafico = 460;
  const altura = porRegra.length * alturaDaLinha + 34;

  const svg = noSvg('svg', {
    class: 'grafico', viewBox: '0 0 900 ' + altura, role: 'img',
    'aria-label': 'Apontamentos e nao avaliados por regra',
  });
  const hachura = definirHachuras(svg, prefixo);

  const maior = Math.max(1, ...porRegra.map((linha) => linha.achado + linha.naoAvaliado));

  porRegra.forEach((linha, indice) => {
    const y = indice * alturaDaLinha + 8;

    svg.appendChild(textoSvg(0, y + 13, linha.regraId, {
      'font-size': 12, fill: 'var(--tinta3)', 'font-family': 'var(--mono)', 'font-weight': 600,
    }));
    svg.appendChild(linha.regraNome
      ? textoSvg(margemEsquerda, y + 13, linha.regraNome, {
        'font-size': 13, fill: 'var(--tinta)', 'font-family': 'var(--sans)', 'font-weight': 600,
      })
      : textoSvg(margemEsquerda, y + 13,
        linha.motivoDoNomeDaRegraAusente || 'sem nome, e a resposta nao disse por que', {
          'font-size': 12, fill: 'var(--tinta3)', 'font-family': 'var(--sans)',
          'font-style': 'italic',
        }));

    const comprimentoAchado = (linha.achado / maior) * larguraDoGrafico;
    const comprimentoNaoAvaliado = (linha.naoAvaliado / maior) * larguraDoGrafico;

    if (linha.achado > 0) {
      svg.appendChild(noSvg('rect', {
        x: margemEsquerda, y: y + 19, width: comprimentoAchado, height: 16,
        fill: hachura.ACHADO, stroke: 'rgba(0,0,0,.14)',
      }));
    }
    if (linha.naoAvaliado > 0) {
      svg.appendChild(noSvg('rect', {
        x: margemEsquerda + comprimentoAchado, y: y + 19,
        width: comprimentoNaoAvaliado, height: 16,
        fill: hachura.NAO_AVALIADO, stroke: 'rgba(0,0,0,.14)',
      }));
    }
    if (linha.achado === 0 && linha.naoAvaliado === 0) {
      svg.appendChild(noSvg('line', {
        x1: margemEsquerda, y1: y + 27, x2: margemEsquerda + 14, y2: y + 27,
        stroke: 'var(--eixo)', 'stroke-width': 2,
      }));
    }

    const conforme = linha.conforme && linha.conforme.valor !== null
      ? Number(linha.conforme.valor).toLocaleString('pt-BR') + ' conf.'
      : 'conf. nao derivavel';

    svg.appendChild(textoSvg(
      margemEsquerda + larguraDoGrafico + 12, y + 31,
      linha.achado.toLocaleString('pt-BR') + ' apont. / '
        + linha.naoAvaliado.toLocaleString('pt-BR') + ' nao aval. / ' + conforme,
      { 'font-size': 11.5, fill: 'var(--tinta2)', 'font-family': 'var(--sans)' },
    ));
  });

  const base = porRegra.length * alturaDaLinha + 8;
  svg.appendChild(noSvg('line', {
    x1: margemEsquerda, y1: base, x2: margemEsquerda + larguraDoGrafico, y2: base,
    stroke: 'var(--eixo)',
  }));
  svg.appendChild(textoSvg(
    margemEsquerda, base + 20,
    'escala: 0 a ' + maior.toLocaleString('pt-BR')
      + ' (maior soma de apontamentos + nao avaliados entre as regras)',
    { 'font-size': 11, fill: 'var(--tinta3)', 'font-family': 'var(--sans)' },
  ));

  return svg;
}

/**
 * Cobertura por regra: avaliados, nao avaliados e sem avaliacao, em 100%.
 *
 * Aqui a barra e proporcional ao total da propria regra, porque cobertura e uma
 * proporcao: o que interessa e a fatia que de fato foi medida.
 *
 * Regra com total zero NAO ganha barra de comprimento zero: ganha um traco
 * tracejado e a frase "total = 0". Barra vazia seria lida como "cobertura zero",
 * e cobertura zero e um numero; aqui nao ha numero nenhum.
 */
export function barrasDeCobertura(porRegra) {
  sequencia += 1;
  const prefixo = 'cobertura' + sequencia;

  const alturaDaLinha = 26;
  const margemEsquerda = 52;
  const larguraDaBarra = 520;
  const altura = Math.max(1, porRegra.length) * alturaDaLinha + 12;

  const svg = noSvg('svg', {
    class: 'grafico', viewBox: '0 0 900 ' + altura, role: 'img',
    'aria-label': 'Cobertura por regra: avaliados, nao avaliados e sem avaliacao',
  });

  const defs = noSvg('defs');
  const semAvaliacao = noSvg('pattern', {
    id: prefixo + '-sem', width: 6, height: 6,
    patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(90)',
  });
  semAvaliacao.appendChild(noSvg('rect', { width: 6, height: 6, fill: 'var(--tinta3)' }));
  semAvaliacao.appendChild(noSvg('line', {
    x1: 0, y1: 0, x2: 0, y2: 6, stroke: '#ffffff', 'stroke-width': 2, opacity: 0.55,
  }));
  defs.appendChild(semAvaliacao);
  svg.appendChild(defs);

  const hachura = definirHachuras(svg, prefixo);

  porRegra.forEach((linha, indice) => {
    const y = indice * alturaDaLinha + 6;
    const avaliados = Number(linha.avaliados || 0);
    const naoAvaliados = Number(linha.nao_avaliados || 0);
    const semLinha = Number(linha.sem_avaliacao || 0);
    const total = Number(linha.total || 0);

    svg.appendChild(textoSvg(0, y + 13, linha.regra_id, {
      'font-size': 12, fill: 'var(--tinta)', 'font-family': 'var(--mono)', 'font-weight': 600,
    }));

    if (total === 0) {
      svg.appendChild(noSvg('line', {
        x1: margemEsquerda, y1: y + 8, x2: margemEsquerda + 20, y2: y + 8,
        stroke: 'var(--eixo)', 'stroke-width': 2, 'stroke-dasharray': '3 3',
      }));
      svg.appendChild(textoSvg(margemEsquerda + 28, y + 12,
        'total = 0: nao ha o que medir nesta regra',
        { 'font-size': 11.5, fill: 'var(--tinta3)', 'font-family': 'var(--sans)' }));
      return;
    }

    const fatias = [
      { valor: avaliados, fill: hachura.CONFORME },
      { valor: naoAvaliados, fill: hachura.NAO_AVALIADO },
      { valor: semLinha, fill: 'url(#' + prefixo + '-sem)' },
    ];
    let x = margemEsquerda;
    for (const fatia of fatias) {
      if (fatia.valor <= 0) {
        continue;
      }
      const comprimento = (fatia.valor / total) * larguraDaBarra;
      svg.appendChild(noSvg('rect', {
        x, y: y + 2, width: comprimento, height: 14,
        fill: fatia.fill, stroke: 'rgba(0,0,0,.14)',
      }));
      x += comprimento;
    }

    svg.appendChild(textoSvg(margemEsquerda + larguraDaBarra + 12, y + 13,
      avaliados.toLocaleString('pt-BR') + ' avaliados / '
        + naoAvaliados.toLocaleString('pt-BR') + ' nao aval. / '
        + semLinha.toLocaleString('pt-BR') + ' sem aval. de '
        + total.toLocaleString('pt-BR'),
      { 'font-size': 11.5, fill: 'var(--tinta2)', 'font-family': 'var(--sans)' }));
  });

  return svg;
}
