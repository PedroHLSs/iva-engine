import { el, trocar } from '../../dom.js';
import * as api from '../api.js';
import { falha } from '../pecas.js';
import { enderecoDaBase, enderecoDoHistorico, enderecoDoResultado, irPara } from '../roteador.js';

const EXTENSOES_ACEITAS = '.xml,.zip';

const ENVIANDO = 'Lendo o arquivo e aplicando as regras cadastradas...';

export async function desenhar(raiz) {
  const situacao = el('p', { classe: 'estado-do-envio', role: 'status', 'aria-live': 'polite' });
  const erro = el('div', {});

  const campo = el('input', {
    type: 'file',
    id: 'arquivo',
    accept: EXTENSOES_ACEITAS,
    classe: 'campo-arquivo',
  });

  let ocupado = false;

  async function enviar(arquivo) {
    if (!arquivo || ocupado) {
      return;
    }
    ocupado = true;
    trocar(erro, []);
    situacao.textContent = ENVIANDO;
    const inicio = Date.now();
    const relogio = setInterval(() => {
      situacao.textContent = ENVIANDO + ' ' + Math.round((Date.now() - inicio) / 1000) + ' s';
    }, 1000);

    try {
      const resultado = await api.analisar(arquivo);
      irPara(enderecoDoResultado(resultado.leitura.id));
    } catch (naoDeuCerto) {
      situacao.textContent = '';
      trocar(erro, [falha(naoDeuCerto)]);
    } finally {
      clearInterval(relogio);
      ocupado = false;
      campo.value = '';
    }
  }

  campo.addEventListener('change', () => enviar(campo.files && campo.files[0]));

  const area = el('div', { classe: 'area-de-envio' }, [
    el('p', { classe: 'area-titulo', texto: 'Arraste a nota aqui' }),
    el('p', { classe: 'area-ou', texto: 'ou' }),
    campo,
    el('label', { classe: 'botao-enviar', for: 'arquivo', texto: 'Escolher arquivo' }),
    el('p', {
      classe: 'formatos-aceitos',
      texto: 'Formatos aceitos: .xml (NF-e/NFC-e) ou arquivos compactados em .zip. '
        + '(Atencao: formato .rar nao suportado.)',
    }),
  ]);

  area.addEventListener('dragover', (evento) => {
    evento.preventDefault();
    area.classList.add('recebendo');
  });
  area.addEventListener('dragleave', () => area.classList.remove('recebendo'));
  area.addEventListener('drop', (evento) => {
    evento.preventDefault();
    area.classList.remove('recebendo');
    const arquivos = evento.dataTransfer && evento.dataTransfer.files;
    enviar(arquivos && arquivos[0]);
  });

  const atalho = (href, icone, texto) => el('a', { classe: 'atalho-fantasma', href }, [
    el('span', { classe: 'atalho-icone', 'aria-hidden': 'true', texto: icone }),
    texto,
  ]);

  trocar(raiz, [
    el('div', { classe: 'tela-de-envio' }, [
      el('h1', { texto: 'Conferir enquadramento de IBS e CBS' }),
      el('p', {
        classe: 'sub',
        texto: 'Faca o upload do XML ou ZIP para conferir o enquadramento tributario produto a produto.',
      }),
      area,
      situacao,
      erro,
      el('nav', { classe: 'atalhos-do-envio', 'aria-label': 'Outras telas' }, [
        atalho(enderecoDoHistorico(), '\u{1F552}', 'Analises anteriores'),
        atalho(enderecoDaBase(), '\u{1F5C4}\u{FE0F}', 'Base tributaria carregada'),
      ]),
    ]),
  ]);
}
