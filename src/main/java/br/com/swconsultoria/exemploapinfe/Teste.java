package br.com.swconsultoria.exemploapinfe;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import br.com.swconsultoria.impressao.model.Impressao;
import br.com.swconsultoria.impressao.service.ImpressaoService;
import br.com.swconsultoria.impressao.util.ImpressaoUtil;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.StatusEnum;
import br.com.swconsultoria.nfe.schema_4.enviNFe.*;
import br.com.swconsultoria.nfe.util.ChaveUtil;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import br.com.swconsultoria.nfe.util.RetornoUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import net.sf.jasperreports.engine.JRException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Teste {

    private static ChaveUtil chaveUtil;
    private static ConfiguracoesNfe configuracoesNfe;
    private static String cnpj;
    private static String modelo;
    private static String tipoEmissao;
    private static String cnf;
    private static int serie;
    private static int numero;
    private static LocalDateTime dataEmissao;

    public static void main(String[] args) {
        try {
            emiteNfe();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void emiteNfe() throws Exception {

        //Inicaliza dados da Nota
        cnpj = "10732644000128";
        modelo = "55";
        serie = 1;
        numero = 92724;
        tipoEmissao = "1";
        cnf = String.format("%08d", new Random().nextInt(99999999));
        dataEmissao = LocalDateTime.now();

        //Inicia as Configurações
        criaConfiguracoes();

        //Inicia Chave
        montaChaveNFe(configuracoesNfe);

        //Cria Dados da NOta
        TEnviNFe enviNFe = criaEnviNFe();
        //Efetua assinatura e Validação
        enviNFe = Nfe.montaNfe(configuracoesNfe, enviNFe, true);

        //Envio Da Nota
        TRetEnviNFe retorno = Nfe.enviarNfe(configuracoesNfe, enviNFe, DocumentoEnum.NFE);

        String xmlFinal;
        //FAz a verificação se o Retorno é Assincrono e consulta o Recibo
        if (RetornoUtil.isRetornoAssincrono(retorno)) {
            br.com.swconsultoria.nfe.schema_4.retConsReciNFe.TRetConsReciNFe tRetConsReciNFe = verificaEnvioAssincrono(retorno);
            RetornoUtil.validaAssincrono(tRetConsReciNFe);
            System.out.println("Status: " + tRetConsReciNFe.getProtNFe().get(0).getInfProt().getCStat());
            System.out.println("Protocolo: " + tRetConsReciNFe.getProtNFe().get(0).getInfProt().getNProt());
            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, tRetConsReciNFe.getProtNFe().get(0));
            System.out.println("Xml Final: " + xmlFinal);
        } else {
            RetornoUtil.validaSincrono(retorno);
            System.out.println("Status: " + retorno.getProtNFe().getInfProt().getCStat());
            System.out.println("Protocolo: " + retorno.getProtNFe().getInfProt().getNProt());
            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());
            System.out.println("Xml Final: " + xmlFinal);
        }

        efetuarImpressaoNFe(xmlFinal);

    }

    private static void efetuarImpressaoNFe(String xmlFinal) throws JRException, ParserConfigurationException, IOException, SAXException {

        //Aqui está pegando o Layout Padrão
        Impressao impressao = ImpressaoUtil.impressaoPadraoNFe(xmlFinal);

        //Faz a impressão em pdf File passando o caminho do Arquivo
        ImpressaoService.impressaoPdfArquivo(impressao, "/d/teste/teste-nfe-live.pdf");
    }

    private static br.com.swconsultoria.nfe.schema_4.retConsReciNFe.TRetConsReciNFe verificaEnvioAssincrono(TRetEnviNFe retorno) throws Exception {
        String recibo = retorno.getInfRec().getNRec();
        int tentativa = 1;
        br.com.swconsultoria.nfe.schema_4.retConsReciNFe.TRetConsReciNFe retornoConsulta = null;
        while (true) {
            retornoConsulta = Nfe.consultaRecibo(configuracoesNfe, recibo, DocumentoEnum.NFE);
            if (retornoConsulta.getCStat().equals(StatusEnum.LOTE_EM_PROCESSAMENTO.getCodigo())) {
                Thread.sleep(1000);
                tentativa++;
                if (tentativa > 10) {
                    // SALVAR O RECIBO E CONSULTA POSTERIORMENTE
                    throw new Exception("Lote em processamento.Aguarde um tempo e tente novamente.");
                }
            } else {
                break;
            }
        }

        return retornoConsulta;
    }

    private static void montaChaveNFe(ConfiguracoesNfe configuracoesNfe) {
        chaveUtil = new ChaveUtil(
                configuracoesNfe.getEstado(),
                cnpj,
                modelo,
                serie,
                numero,
                tipoEmissao,
                cnf,
                dataEmissao);
    }

    private static TEnviNFe criaEnviNFe() {
        TEnviNFe enviNFe = new TEnviNFe();
        enviNFe.setVersao(ConstantesUtil.VERSAO.NFE);
        enviNFe.setIdLote("1");
        enviNFe.setIndSinc("1");

        TNFe nfe = new TNFe();
        TNFe.InfNFe infNFe = getInfNFe();
        nfe.setInfNFe(infNFe);
        enviNFe.getNFe().add(nfe);

        return enviNFe;
    }

    private static TNFe.InfNFe getInfNFe() {
        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId(chaveUtil.getChaveNF());
        infNFe.setVersao(ConstantesUtil.VERSAO.NFE);

        infNFe.setIde(montaIde());
        infNFe.setEmit(montaEmitente());
        infNFe.setDest(montaDestinatario());
        infNFe.getDet().addAll(montaDet());
        infNFe.setTransp(montaTransportadora());
        infNFe.setPag(pagamento());
//        infNFe.setInfAdic();
        infNFe.setInfRespTec(montaRespTecnico());
        infNFe.setTotal(montaTotal());

        return infNFe;
    }

    private static TNFe.InfNFe.Total montaTotal() {
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icmsTot = new TNFe.InfNFe.Total.ICMSTot();
        icmsTot.setVBC("10.00");
        icmsTot.setVICMS("1.00");
        icmsTot.setVICMSDeson("0.00");
        icmsTot.setVFCP("0.00");
        icmsTot.setVBCST("0.00");
        icmsTot.setVST("0.00");
        icmsTot.setVFCPST("0.00");
        icmsTot.setVFCPSTRet("0.00");
        icmsTot.setVProd("10.00");
        icmsTot.setVFrete("0.00");
        icmsTot.setVSeg("0.00");
        icmsTot.setVDesc("0.00");
        icmsTot.setVII("0.00");
        icmsTot.setVIPI("0.00");
        icmsTot.setVIPIDevol("0.00");
        icmsTot.setVPIS("0.17");
        icmsTot.setVCOFINS("0.76");
        icmsTot.setVOutro("0.00");
        icmsTot.setVNF("10.00");
        total.setICMSTot(icmsTot);

        return total;
    }

    private static TInfRespTec montaRespTecnico() {
        TInfRespTec respTec = new TInfRespTec();
        respTec.setCNPJ("32330160000195");
        respTec.setXContato("Samuel Oliveira");
        respTec.setEmail("samuel@swconsultoria.com.br");
        respTec.setFone("62993066546");
        return respTec;
    }

    private static TNFe.InfNFe.Pag pagamento() {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag detPag = new TNFe.InfNFe.Pag.DetPag();
        detPag.setTPag("01");
        detPag.setVPag("10.00");
        pag.getDetPag().add(detPag);
        return pag;
    }

    private static TNFe.InfNFe.Transp montaTransportadora() {
        TNFe.InfNFe.Transp transp = new TNFe.InfNFe.Transp();
        transp.setModFrete("9");
        return transp;
    }

    private static List<TNFe.InfNFe.Det> montaDet() {
        TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
        det.setNItem("1");
        det.setProd(montaProduto());
        det.setImposto(montaImposto());
        return Collections.singletonList(det);
    }

    private static TNFe.InfNFe.Det.Imposto montaImposto() {
        TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();
        criaImpostoIcms(imposto);
        criaImpostoPis(imposto);
        criaImpostoCofins(imposto);
        return imposto;
    }

    private static void criaImpostoPis(TNFe.InfNFe.Det.Imposto imposto) {
        TNFe.InfNFe.Det.Imposto.PIS pis = new TNFe.InfNFe.Det.Imposto.PIS();
        TNFe.InfNFe.Det.Imposto.PIS.PISAliq pisAliq = new TNFe.InfNFe.Det.Imposto.PIS.PISAliq();
        pisAliq.setCST("01");
        pisAliq.setVBC("10.00");
        pisAliq.setPPIS("1.65");
        pisAliq.setVPIS("0.17");
        pis.setPISAliq(pisAliq);
        imposto.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoPIS(pis));
    }

    private static void criaImpostoCofins(TNFe.InfNFe.Det.Imposto imposto) {
        TNFe.InfNFe.Det.Imposto.COFINS cofins = new TNFe.InfNFe.Det.Imposto.COFINS();
        TNFe.InfNFe.Det.Imposto.COFINS.COFINSAliq cofinsAliq = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSAliq();
        cofinsAliq.setCST("01");
        cofinsAliq.setVBC("10.00");
        cofinsAliq.setPCOFINS("7.60");
        cofinsAliq.setVCOFINS("0.76");
        cofins.setCOFINSAliq(cofinsAliq);
        imposto.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoCOFINS(cofins));
    }

    private static void criaImpostoIcms(TNFe.InfNFe.Det.Imposto imposto) {
        TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
        TNFe.InfNFe.Det.Imposto.ICMS.ICMS00 icms00 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMS00();
        icms00.setOrig("0");
        icms00.setModBC("0");
        icms00.setCST("00");
        icms00.setVBC("10.00");
        icms00.setPICMS("10");
        icms00.setVICMS("1.00");
        icms.setICMS00(icms00);
        imposto.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoICMS(icms));
    }

    private static TNFe.InfNFe.Det.Prod montaProduto() {
        TNFe.InfNFe.Det.Prod produto = new TNFe.InfNFe.Det.Prod();
        produto.setCProd("123");
        produto.setCEAN("SEM GTIN");
        produto.setXProd("Produto XYZ"); // "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL"
        produto.setNCM("27101932");
        produto.setCEST("0600500");
        produto.setIndEscala("S");
        produto.setCFOP("6405");
        produto.setUCom("UN");
        produto.setQCom("1");
        produto.setVUnCom("10");
        produto.setVProd("10.00");
        produto.setCEANTrib("SEM GTIN");
        produto.setUTrib("UN");
        produto.setQTrib("1");
        produto.setVUnTrib("10");
        produto.setIndTot("1");
        return produto;
    }

    private static TNFe.InfNFe.Dest montaDestinatario() {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        dest.setXNome("NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
        dest.setCNPJ("10732644000128");
        dest.setIE("104519304");
        dest.setIndIEDest("1");

        TEndereco enderecoEmitente = new TEndereco();
        enderecoEmitente.setXLgr("Rua Teste");
        enderecoEmitente.setNro("0");
        enderecoEmitente.setXCpl("Qd 1 Lote 1");
        enderecoEmitente.setXBairro("Centro");
        enderecoEmitente.setCMun("5219753");
        enderecoEmitente.setXMun("SANTO ANTONIO DO DESCOBERTO");
        enderecoEmitente.setUF(TUf.GO);
        enderecoEmitente.setCEP("74000000");
        dest.setEnderDest(enderecoEmitente);

        return dest;
    }

    private static TNFe.InfNFe.Emit montaEmitente() {
        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        emit.setXNome("Nome Empresa");
        emit.setCNPJ(cnpj);
        emit.setIE("104519304");
        emit.setCRT("3");

        TEnderEmi enderecoEmitente = new TEnderEmi();
        enderecoEmitente.setXLgr("Rua Teste");
        enderecoEmitente.setNro("0");
        enderecoEmitente.setXCpl("Qd 1 Lote 1");
        enderecoEmitente.setXBairro("Centro");
        enderecoEmitente.setCMun("5219753");
        enderecoEmitente.setXMun("SANTO ANTONIO DO DESCOBERTO");
        enderecoEmitente.setUF(TUfEmi.valueOf(configuracoesNfe.getEstado().toString()));
        enderecoEmitente.setCEP("74000000");
        emit.setEnderEmit(enderecoEmitente);
        return emit;
    }

    private static TNFe.InfNFe.Ide montaIde() {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(configuracoesNfe.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("Venda NFe");
        ide.setMod(modelo);
        ide.setSerie(String.valueOf(serie));
        ide.setNNF(String.valueOf(numero));
        ide.setDhEmi(XmlNfeUtil.dataNfe(dataEmissao));
        ide.setTpNF("1");
        ide.setIdDest("2");
        ide.setCMunFG("5219753");
        ide.setTpImp("1");
        ide.setTpEmis(tipoEmissao);
        ide.setCDV(chaveUtil.getDigitoVerificador());
        ide.setTpAmb(configuracoesNfe.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0.0");
        return ide;
    }

    private static void criaConfiguracoes() throws CertificadoException, FileNotFoundException {
        Certificado certificado = CertificadoService.certificadoPfx("/d/teste/certificado.pfx", "12345");
        configuracoesNfe = ConfiguracoesNfe.criarConfiguracoes(EstadosEnum.GO, AmbienteEnum.HOMOLOGACAO, certificado, "/d/teste/nfe/schemas");
    }
}
