package mz.ac.isutc.scad.no.documentos;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

import mz.ac.isutc.scad.no.loja.PedidoCliente;

/** Gera o recibo de um pedido da loja autorizado, em PDF, com OpenPDF. */
public final class GeradorRecibo {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Africa/Maputo"));
    private static final Color VERDE = new Color(21, 128, 61);

    private GeradorRecibo() {
    }

    public static byte[] gerar(PedidoCliente p, String cidade, String emitidoPor, Instant emitidoEm) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, saida);
        doc.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, VERDE);
        Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font pequeno = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);

        doc.add(new Paragraph("SCAD — Loja · Armazém " + p.armazemId() + " (" + cidade + ")", pequeno));
        doc.add(new Paragraph("RECIBO " + p.id(), titulo));
        doc.add(new Paragraph(" "));

        PdfPTable dados = new PdfPTable(new float[] {1.4f, 3f});
        dados.setWidthPercentage(100);
        linha(dados, "Cliente", p.clienteNome(), negrito, normal);
        linha(dados, "Telefone", p.clienteTelefone(), negrito, normal);
        linha(dados, "Data da compra", DATA_HORA.format(p.criadoEm()), negrito, normal);
        linha(dados, "Autorizado em", p.decididoEm() == null ? "-" : DATA_HORA.format(p.decididoEm()), negrito, normal);
        linha(dados, "Pagamento", "Simulado — ref. " + p.pagamentoRef(), negrito, normal);
        doc.add(dados);
        doc.add(new Paragraph(" "));

        PdfPTable linhas = new PdfPTable(new float[] {3.2f, 1f, 1.8f, 2f});
        linhas.setWidthPercentage(100);
        for (String c : new String[] {"Produto", "Qtd.", "Preço unit.", "Subtotal"}) {
            PdfPCell cel = new PdfPCell(new Phrase(c, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cel.setBackgroundColor(VERDE);
            linhas.addCell(cel);
        }
        for (PedidoCliente.Linha l : p.linhas()) {
            linhas.addCell(new Phrase(l.produtoNome(), normal));
            linhas.addCell(aDireita(String.valueOf(l.quantidade()), normal));
            linhas.addCell(aDireita(Dinheiro.formatar(l.precoUnitario()), normal));
            linhas.addCell(aDireita(Dinheiro.formatar(l.subtotal()), normal));
        }
        PdfPCell rotulo = new PdfPCell(new Phrase("TOTAL", negrito));
        rotulo.setColspan(3);
        linhas.addCell(rotulo);
        linhas.addCell(aDireita(Dinheiro.formatar(p.total()), negrito));
        doc.add(linhas);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Emitido pelo nó " + emitidoPor + " em " + DATA_HORA.format(emitidoEm)
                + " (hora corrigida). Arquivado no HDFS (replicação 3).", pequeno));
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
}
