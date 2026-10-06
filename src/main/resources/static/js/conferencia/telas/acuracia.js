import { el, trocar } from '../../dom.js';
import { inteiro } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import * as api from '../api.js';
import { faixaDeNatureza, falha } from '../pecas.js';
import { quemEsta } from '../../sessao.js';

export async function desenhar(raiz) {
  const [previa, usuario] = await Promise.all([api.previaDaMedicao(), quemEsta()]);
  const resultado = el('div', { 'aria-live': 'polite' });
  const aviso = el('div', { classe: 'ac-aviso-texto' });
  const estado = { previa };

  function mostrarAviso(novaPrevia) {
    estado.previa = novaPrevia;
    trocar(aviso, [el('strong', { texto: 'Antes de medir: ' }), novaPrevia.aviso]);
  }
  mostrarAviso(previa);

  const podeMedir = usuario && usuario.permissoes.enviarNota;
  const info = botaoDeInformacao('Como a medicao funciona', [
    el('p', {
      texto: 'Envie as notas e o gabarito rotulado a mao. O sistema roda as regras de novo sobre as notas, '
        + 'compara com o gabarito e mostra precisao, recall, F1 e cobertura, por regra e no consolidado. '
        + 'Nada e gravado.',
    }),
  ]);
  trocar(raiz, [
    el('div', { classe: 'tela-acuracia-conf' }, [
      el('header', { classe: 'ac-cabecalho' }, [
        el('div', { classe: 'ac-titulo' }, [el('h1', { texto: 'Acuracia do motor' }), info.botao]),
        info.balao,
      ]),
      el('div', { classe: 'ac-aviso', role: 'note' }, [
        el('span', { classe: 'ac-aviso-icone', 'aria-hidden': 'true', texto: '⚠' }),
        aviso,
      ]),
      podeMedir
        ? formulario(estado, mostrarAviso, resultado)
        : el('p', { classe: 'ac-nota', texto: 'Medir sela a carga de catalogo, e por isso e do perfil fiscal ou administrador.' }),
      resultado,
    ]),
  ]);
}

function seletor(id, rotulo, accept, ajuda) {
  const campo = el('input', {
    type: 'file', id, accept, classe: 'ac-arquivo-nativo', 'aria-required': 'true',
    'aria-describedby': id + '-escolhido ' + id + '-ajuda',
  });
  const escolhido = el('span', { classe: 'ac-escolhido', id: id + '-escolhido', texto: 'Nenhum arquivo escolhido' });
  campo.addEventListener('change', () => {
    escolhido.textContent = campo.files[0] ? campo.files[0].name : 'Nenhum arquivo escolhido';
  });
  const bloco = el('div', { classe: 'ac-campo' }, [
    el('span', { classe: 'ac-rotulo', texto: rotulo }),
    el('div', { classe: 'ac-seletor' }, [
      campo,
      el('label', { for: id, classe: 'ac-botao', texto: 'Escolher arquivo' }),
      escolhido,
    ]),
    el('p', { classe: 'ac-ajuda', id: id + '-ajuda', texto: ajuda }),
  ]);
  return { campo, bloco };
}

function formulario(estado, mostrarAviso, resultado) {
  const seletorDasNotas = seletor('notas', 'Notas', '.zip,.xml', 'Um .zip com os XML, ou um .xml.');
  const seletorDoGabarito = seletor('gabarito', 'Gabarito', '.csv,text/csv',
    '.csv com chave_documento; numero_item; regra_id; rotulo_esperado.');
  const notas = seletorDasNotas.campo;
  const gabarito = seletorDoGabarito.campo;
  const botao = el('button', { type: 'submit', classe: 'ac-botao', texto: 'Medir' });

  const form = el('form', { classe: 'ac-cartao' }, [
    seletorDasNotas.bloco,
    seletorDoGabarito.bloco,
    el('div', { classe: 'ac-acoes' }, [botao]),
  ]);

  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (!notas.files[0] || !gabarito.files[0]) {
      trocar(resultado, [el('div', { classe: 'aviso erro', texto: 'Escolha os dois arquivos: as notas e o gabarito.' })]);
      return;
    }
    botao.disabled = true;
    const inicio = Date.now();
    const andamento = el('p', { classe: 'estado-do-envio', role: 'status' });
    const relogio = setInterval(() => {
      andamento.textContent = 'Rodando as regras sobre as notas e comparando com o gabarito... '
        + Math.round((Date.now() - inicio) / 1000) + ' s';
    }, 1000);
    andamento.textContent = 'Enviando os arquivos...';
    trocar(resultado, [andamento]);
    try {
      const medicao = await api.medirAcuracia(notas.files[0], gabarito.files[0], estado.previa.versaoDaCarga);
      trocar(resultado, [relatorio(medicao)]);
      mostrarAviso(await api.previaDaMedicao());
    } catch (recusa) {
      if (recusa.codigo === 'CARGA_MUDOU') {
        mostrarAviso(await api.previaDaMedicao());
        trocar(resultado, [el('div', { classe: 'aviso erro' }, [
          el('strong', { texto: 'Nada foi medido e nada foi selado. ' }), recusa.message,
          ' Confira o aviso acima e meça de novo se ainda quiser.',
        ])]);
      } else {
        trocar(resultado, [falha(recusa)]);
      }
    } finally {
      clearInterval(relogio);
      botao.disabled = false;
    }
  });
  return form;
}

function relatorio(medicao) {
  const linhas = [...medicao.porRegra, medicao.consolidado].map((linha) => el('tr', {
    classe: linha.regraId ? null : 'linha-consolidada',
  }, [
    el('th', { scope: 'row', texto: linha.rotulo + (linha.regraId ? ' (' + linha.regraId + ')' : '') }),
    celula(linha.precisao), celula(linha.recall), celula(linha.f1),
    el('td', {}, [celula(linha.cobertura, true), el('span', {
      classe: 'nota', texto: ' ' + inteiro(linha.avaliados) + ' de ' + inteiro(linha.total) + ' avaliados',
    })]),
    el('td', { classe: 'numero', texto: inteiro(linha.verdadeirosPositivos) }),
    el('td', { classe: 'numero', texto: inteiro(linha.falsosPositivos) }),
    el('td', { classe: 'numero', texto: inteiro(linha.falsosNegativos) }),
    el('td', { classe: 'numero', texto: inteiro(linha.verdadeirosNegativos) }),
    el('td', { classe: 'numero', texto: inteiro(linha.naoAvaliados) }),
    el('td', { classe: 'mono', texto: linha.versaoDoCatalogo }),
    el('td', { classe: 'mono', texto: linha.versaoDoConjuntoDeRegras }),
  ]));

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Resultado da medicao' }),
    faixaDeNatureza(medicao.natureza),
    el('dl', { classe: 'identificacao identificacao-da-medicao' }, [
      el('dt', { texto: 'versao do catalogo' }), el('dd', { classe: 'mono', texto: medicao.versaoDoCatalogo }),
      el('dt', { texto: 'versao das regras' }), el('dd', { classe: 'mono', texto: medicao.versaoDoConjuntoDeRegras }),
      el('dt', { texto: 'tolerancia R05' }), el('dd', { texto: (medicao.toleranciaDeValor && medicao.toleranciaDeValor.texto)
        || (medicao.toleranciaDeValor && medicao.toleranciaDeValor.motivoDaAusencia) || 'a resposta nao trouxe a tolerancia' }),
      el('dt', { texto: 'cobertura do consolidado' }),
      el('dd', {}, [celula(medicao.consolidado.cobertura, true), document.createTextNode(
        ' (' + inteiro(medicao.consolidado.avaliados) + ' avaliados de ' + inteiro(medicao.consolidado.total) + ')')]),
      el('dt', { texto: 'documentos / itens' }),
      el('dd', { texto: inteiro(medicao.documentosAuditados) + ' / ' + inteiro(medicao.itensAuditados) }),
      el('dt', { texto: 'fora da medicao' }),
      el('dd', {
        texto: inteiro(medicao.avaliacoesSemLinhaNoGabarito) + ' avaliacao(oes) sem linha no gabarito; '
          + inteiro(medicao.linhasDoGabaritoSemAvaliacao) + ' linha(s) do gabarito sem avaliacao',
      }),
    ]),
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: 'regra' }),
          el('th', { scope: 'col', texto: 'precisao' }),
          el('th', { scope: 'col', texto: 'recall' }),
          el('th', { scope: 'col', texto: 'F1' }),
          el('th', { scope: 'col', texto: 'cobertura' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'VP' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'FP' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'FN' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'VN' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'nao avaliados' }),
          el('th', { scope: 'col', texto: 'catalogo' }),
          el('th', { scope: 'col', texto: 'regras' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
    el('p', { classe: 'nota', texto: medicao.comoOConsolidadoEObtido }),
    el('p', { classe: 'nota', texto: medicao.porQueNaoHaMediaMacro }),
    el('p', { classe: 'nota', texto: medicao.comoONaoAvaliadoEntra }),
  ]);
}

function celula(metrica, semTd) {
  const conteudo = el('span', {
    classe: metrica.valor === null ? 'metrica-indefinida' : 'mono',
    title: metrica.motivoDaIndefinicao || null,
    texto: metrica.texto,
  });
  return semTd ? conteudo : el('td', {}, [conteudo]);
}
