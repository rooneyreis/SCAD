package mz.ac.isutc.scad.no.documentos;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import mz.ac.isutc.scad.no.ordens.OrdemCompra;

/** Gera a factura (nota de encomenda) de uma ordem de compra em PDF, com OpenPDF. */
public final class GeradorFactura {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Africa/Maputo"));
    private static final Color AZUL = new Color(31, 78, 121);

    private GeradorFactura() {
    }

    public static byte[] gerar(OrdemCompra o, String emitidoPor, Instant emitidoEm) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(doc, saida);
        doc.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, AZUL);
        Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font pequeno = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        doc.add(new Paragraph("SCAD — Sistema de Compras Automáticas Distribuídas", pequeno));
        doc.add(new Paragraph("FACTURA / NOTA DE ENCOMENDA " + o.id(), titulo));
        doc.add(new Paragraph(" "));

        PdfPTable dados = new PdfPTable(new float[] {1.3f, 3f});
        dados.setWidthPercentage(100);
        linha(dados, "Fornecedor", o.fornecedorNome() + " (" + o.fornecedorId() + ")", subtitulo, normal);
        linha(dados, "Canal", o.protocolo() + (o.protocolo().equals("SOAP") ? " / XML" : " / JSON"), subtitulo, normal);
        linha(dados, "Confirmação do fornecedor", valor(o.confirmacao()), subtitulo, normal);
        linha(dados, "Prazo de entrega", o.prazoEntrega() == null ? "-" : o.prazoEntrega().toString(), subtitulo, normal);
        linha(dados, "Produto", o.produtoNome() + " (" + o.produtoId() + ")", subtitulo, normal);
        linha(dados, "Quantidade total (1 lote)", String.valueOf(o.quantidadeTotal()), subtitulo, normal);
        linha(dados, "Preço unitário (custo)", Dinheiro.formatar(o.precoUnitario()), subtitulo, normal);
        linha(dados, "TOTAL", Dinheiro.formatar(o.total()), subtitulo, subtitulo);
        linha(dados, "Armazém que disparou", o.armazemOrigem(), subtitulo, normal);
        linha(dados, "Nó coordenador (2PC)", o.coordenador(), subtitulo, normal);
        linha(dados, "Data da ordem (hora corrigida)", DATA_HORA.format(o.tsCorrigido()), subtitulo, normal);
        linha(dados, "Carimbo de Lamport", String.valueOf(o.tsLamport()), subtitulo, normal);
        doc.add(dados);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Repartição da entrega pelos armazéns", subtitulo));
        doc.add(new Paragraph(" "));
        PdfPTable linhas = new PdfPTable(new float[] {2.2f, 1.3f, 2f, 2.2f});
        linhas.setWidthPercentage(100);
        linhas.setHorizontalAlignment(Element.ALIGN_LEFT);
        cabecalho(linhas, "Armazém");
        cabecalho(linhas, "Quantidade");
        cabecalho(linhas, "Preço unitário");
        cabecalho(linhas, "Subtotal");
        for (Map.Entry<String, Integer> l : o.linhas().entrySet()) {
            linhas.addCell(new Phrase(l.getKey(), normal));
            linhas.addCell(aDireita(String.valueOf(l.getValue()), normal));
            linhas.addCell(aDireita(Dinheiro.formatar(o.precoUnitario()), normal));
            linhas.addCell(aDireita(Dinheiro.formatar(o.precoUnitario().multiply(BigDecimal.valueOf(l.getValue()))), normal));
        }
        PdfPCell rotuloTotal = new PdfPCell(new Phrase("TOTAL", subtitulo));
        rotuloTotal.setColspan(3);
        linhas.addCell(rotuloTotal);
        linhas.addCell(aDireita(Dinheiro.formatar(o.total()), subtitulo));
        doc.add(linhas);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Emitida pelo nó " + emitidoPor + " em " + DATA_HORA.format(emitidoEm)
                + ". Documento arquivado no HDFS (replicação 3).", pequeno));
        doc.close();
        return saida.toByteArray();
    }

    private static void linha(PdfPTable t, String rotulo, String valor, Font fr, Font fv) {
        PdfPCell a = new PdfPCell(new Phrase(rotulo, fr));
        a.setBorderColor(Color.LIGHT_GRAY);
        PdfPCell b = new PdfPCell(new Phrase(valor, fv));
        b.setBorderColor(Color.LIGHT_GRAY);
        t.addCell(a);
        t.addCell(b);
    }

    private static PdfPCell aDireita(String texto, Font fonte) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fonte));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static void cabecalho(PdfPTable t, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        c.setBackgroundColor(AZUL);
        t.addCell(c);
    }

    private static String valor(String s) {
        return s == null ? "-" : s;
    }
}
