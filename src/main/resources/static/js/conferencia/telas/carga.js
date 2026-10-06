import { el, trocar } from '../../dom.js';
import { dataHora, inteiro } from '../../formato.js';
import { escrever, ler, quemEsta } from '../../sessao.js';
import { falha, faixaDeNatureza } from '../pecas.js';
import { enderecoDaCarga, enderecoDasCargas, irPara } from '../roteador.js';
import { ARQUIVO_OPCIONAL, ARQUIVOS_DA_CARGA, tabelaDeRecusas } from './cargas.js';

export async function desenhar(raiz, _parametros, rota) {
  const [carga, usuario] = await Promise.all([
    ler('/api/cargas/' + encodeURIComponent(rota.versao)),
    quemEsta(),
  ]);
  const podeAdministrar = usuario && usuario.permissoes.administrar;

  trocar(raiz, [
    el('a', { classe: 'voltar', href: enderecoDasCargas(), texto: 'Cargas de catalogo' }),
    el('h1', { texto: 'Carga "' + carga.versao + '"' }),
    faixaDeNatureza(carga.natureza),
    estado(carga),
    podeAdministrar ? edicao(carga) : null,
    podeAdministrar ? exclusao(carga) : null,
  ]);
}

function estado(carga) {
  return el('section', { classe: 'bloco' }, [
    el('dl', { classe: 'identificacao' }, [
      el('dt', { texto: 'importada em' }), el('dd', { texto: dataHora(carga.importadoEm) }),
      el('dt', { texto: 'selo' }),
      el('dd', { texto: carga.selada ? 'selada em ' + dataHora(carga.seladaEm) : carga.motivoDoSeloAusente }),
      el('dt', { texto: 'analises que a usam' }), el('dd', { texto: inteiro(carga.analisesQueUsam) }),
      el('dt', { texto: 'origem' }),
      el('dd', { texto: carga.derivadaDe ? 'editada a partir de ' + carga.derivadaDe : carga.motivoDaOrigemAusente }),
      el('dt', { texto: 'alterada em' }),
      el('dd', { texto: carga.alteradaEm ? dataHora(carga.alteradaEm) : carga.motivoDaAlteracaoAusente }),
      el('dt', { texto: 'registros' }),
      el('dd', {
        texto: inteiro(carga.registros.classificacoesTributarias) + ' classificacoes, '
          + inteiro(carga.registros.registrosDeNcm) + ' NCM, '
          + inteiro(carga.registros.itensDeAnexo) + ' itens de anexo, '
          + inteiro(carga.registros.aliquotas) + ' aliquotas',
      }),
    ]),
    carga.maisRecente
      ? el('p', { classe: 'nota', texto: 'E a carga mais recente: e ela que a proxima analise vai usar.' })
      : null,
  ]);
}

function edicao(carga) {
  const resultado = el('div', { role: 'status' });
  const efeitoVisto = { previa: carga.edicao };
  const aviso = el('div', { classe: 'aviso' });
  const botao = el('button', { type: 'submit', classe: 'botao principal' });
  const arquivos = el('input', { type: 'file', id: 'arquivos-da-edicao', multiple: 'multiple', accept: '.csv' });

  function mostrarPrevia(previa) {
    efeitoVisto.previa = previa;
    trocar(aviso, [el('strong', { texto: 'Antes de salvar: ' }), previa.aviso]);
    botao.textContent = previa.efeito === 'CRIAR_VERSAO_NOVA'
      ? 'Salvar criando a versao "' + previa.versaoQueSeraCriada + '"'
      : 'Salvar alterando o rascunho "' + carga.versao + '"';
  }
  mostrarPrevia(carga.edicao);

  const formulario = el('form', { classe: 'formulario' }, [
    el('h2', { texto: 'Editar' }),
    aviso,
    el('label', {
      for: 'arquivos-da-edicao',
      texto: 'Um ou mais CSV, com o mesmo nome da tabela que substituem (' + ARQUIVOS_DA_CARGA.join(', ')
        + ', ' + ARQUIVO_OPCIONAL + '). A tabela que nao vier e mantida como esta. Trocar '
        + 'classificacao-tributaria.csv, registro-ncm.csv ou item-anexo.csv exige cobertura.csv junto; '
        + 'trocar item-anexo.csv exige ' + ARQUIVO_OPCIONAL + ' junto quando a carga declara anexo carregado.',
    }),
    arquivos,
    el('div', { classe: 'acoes' }, [botao]),
    resultado,
  ]);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const previa = efeitoVisto.previa;
    const envio = new FormData();
    envio.append('efeitoEsperado', previa.efeito);
    if (previa.versaoQueSeraCriada) {
      envio.append('versaoNova', previa.versaoQueSeraCriada);
    }
    for (const arquivo of arquivos.files) {
      envio.append('arquivos', arquivo, arquivo.name);
    }
    trocar(resultado, [el('p', { classe: 'estado-do-envio', texto: 'Lendo os arquivos...' })]);
    try {
      const feita = await escrever('PUT', '/api/cargas/' + encodeURIComponent(carga.versao), envio);
      trocar(resultado, [el('div', { classe: 'aviso' }, [
        el('strong', { texto: 'Salvo. ' }), feita.explicacao, ' ',
        el('a', { href: enderecoDaCarga(feita.versaoResultante), texto: 'Abrir "' + feita.versaoResultante + '"' }),
      ])]);
    } catch (recusa) {
      if (recusa.codigo === 'EDICAO_DESATUALIZADA' && recusa.corpo && recusa.corpo.previaAtual) {
        mostrarPrevia(recusa.corpo.previaAtual);
        trocar(resultado, [el('div', { classe: 'aviso erro' }, [
          el('strong', { texto: 'Nada foi gravado. ' }), recusa.message,
          ' Confira o aviso acima e salve de novo se ainda quiser.',
        ])]);
      } else {
        trocar(resultado, [tabelaDeRecusas(recusa)]);
      }
    }
  });
  return formulario;
}

function exclusao(carga) {
  if (!carga.exclusao.permitida) {
    return el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Excluir' }),
      el('p', { texto: 'Nao pode ser excluida: ' + carga.exclusao.motivo + '.' }),
    ]);
  }
  const resultado = el('div', {});
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Excluir' }),
    el('p', { classe: 'nota', texto: carga.exclusao.motivo }),
    el('button', {
      type: 'button', classe: 'botao',
      texto: 'Excluir o rascunho "' + carga.versao + '"',
      aoClicar: async () => {
        const aviso = carga.maisRecente
          ? ' Ela e a mais recente: sem ela, a carga anterior volta a ser a usada nas proximas analises.'
          : '';
        if (!window.confirm('Excluir a carga "' + carga.versao + '"?' + aviso)) {
          return;
        }
        try {
          await escrever('DELETE', '/api/cargas/' + encodeURIComponent(carga.versao));
          irPara(enderecoDasCargas());
        } catch (recusa) {
          trocar(resultado, [falha(recusa)]);
        }
      },
    }),
    resultado,
  ]);
}
