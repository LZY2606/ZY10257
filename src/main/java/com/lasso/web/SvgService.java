package com.lasso.web;

import com.lasso.engine.LassoChecker;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.ReplayResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务器端生成套索 SVG：状态节点、转移卫式、入口/接受/公平标记与可选“已删除”灰显。 */
@Service
public class SvgService {

    public String render(Model model, Counterexample ce, String entry,
                         ReplayResult replay, List<String> deletedStates,
                         List<String> keptVariables) {
        List<String> trace = ce.trace();
        Map<String, double[]> positions = layout(trace);
        StringBuilder svg = new StringBuilder();
        int width = Math.max(900, 120 + trace.size() * 130);
        int height = 320;
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\" aria-label=\"套索状态图\">");
        svg.append("<defs><marker id=\"arrow\" markerWidth=\"9\" markerHeight=\"9\" refX=\"8\" refY=\"3\" ")
                .append("orient=\"auto\" markerUnits=\"strokeWidth\">")
                .append("<path d=\"M0,0 L0,6 L9,3 z\" fill=\"#475569\"/></marker>");
        svg.append("<marker id=\"arrowBad\" markerWidth=\"9\" markerHeight=\"9\" refX=\"8\" refY=\"3\" ")
                .append("orient=\"auto\" markerUnits=\"strokeWidth\">")
                .append("<path d=\"M0,0 L0,6 L9,3 z\" fill=\"#dc2626\"/></marker></defs>");

        int entryIndex = trace.indexOf(entry);
        for (int i = 0; i < trace.size() - 1; i++) {
            drawEdge(svg, model, trace.get(i), trace.get(i + 1), positions, false);
        }
        if (entryIndex >= 0) {
            drawEdge(svg, model, trace.get(trace.size() - 1), entry, positions, true);
        }
        for (String stateId : trace) {
            drawNode(svg, model, stateId, positions, stateId.equals(entry),
                    deletedStates.contains(stateId), keptVariables);
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private Map<String, double[]> layout(List<String> trace) {
        Map<String, double[]> positions = new LinkedHashMap<>();
        int x = 90;
        int y = 150;
        int step = 120;
        for (String id : trace) {
            positions.putIfAbsent(id, new double[]{x, y});
            x += step;
        }
        return positions;
    }

    private void drawNode(StringBuilder svg, Model model, String stateId,
                          Map<String, double[]> positions, boolean isEntry,
                          boolean deleted, List<String> keptVariables) {
        double[] p = positions.get(stateId);
        ModelState state = model.state(stateId);
        int cx = (int) p[0];
        int cy = (int) p[1];
        String fill = deleted ? "#e5e7eb" : "#eef2ff";
        String stroke = isEntry ? "#b45309" : "#334155";
        double strokeWidth = isEntry ? 3 : 1.5;
        svg.append("<g>");
        svg.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy)
                .append("\" r=\"26\" fill=\"").append(fill)
                .append("\" stroke=\"").append(stroke)
                .append("\" stroke-width=\"").append(strokeWidth).append("\"/>");
        svg.append("<text x=\"").append(cx).append("\" y=\"").append(cy + 4)
                .append("\" text-anchor=\"middle\" font-size=\"13\" font-family=\"monospace\">")
                .append(escape(stateId)).append("</text>");
        if (isEntry) {
            svg.append("<text x=\"").append(cx).append("\" y=\"").append(cy - 34)
                    .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#b45309\">循环入口</text>");
        }
        StringBuilder tags = new StringBuilder();
        for (String a : state.acceptSets()) {
            tags.append("<tspan fill=\"#15803d\">◇").append(escape(a)).append(" </tspan>");
        }
        for (String f : state.fairSets()) {
            tags.append("<tspan fill=\"#7c3aed\">♢").append(escape(f)).append(" </tspan>");
        }
        if (tags.length() > 0) {
            svg.append("<text x=\"").append(cx).append("\" y=\"").append(cy + 46)
                    .append("\" text-anchor=\"middle\" font-size=\"11\" font-family=\"monospace\">")
                    .append(tags).append("</text>");
        }
        if (!keptVariables.isEmpty()) {
            StringBuilder vars = new StringBuilder();
            state.variables().forEach((k, v) -> {
                if (keptVariables.contains(k)) {
                    vars.append(escape(k)).append('=').append(escape(String.valueOf(v))).append(' ');
                }
            });
            svg.append("<text x=\"").append(cx).append("\" y=\"").append(cy + 62)
                    .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"#475569\" font-family=\"monospace\">")
                    .append(vars.toString().trim()).append("</text>");
        }
        if (deleted) {
            svg.append("<text x=\"").append(cx).append("\" y=\"").append(cy - 48)
                    .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"#6b7280\">已删除</text>");
        }
        svg.append("</g>");
    }

    private void drawEdge(StringBuilder svg, Model model, String from, String to,
                          Map<String, double[]> positions, boolean closure) {
        double[] a = positions.get(from);
        double[] b = positions.get(to);
        String guard = firstGuard(model, from, to);
        if (closure) {
            if (from.equals(to)) {
                svg.append("<path d=\"M").append((int) a[0] + 20).append(',').append((int) a[1] - 24)
                        .append(" a20,20 0 1,1 0.1,0\" fill=\"none\" stroke=\"#b45309\" stroke-width=\"2\"")
                        .append(" marker-end=\"url(#arrow)\"/>");
                svg.append("<text x=\"").append((int) a[0] + 34).append("\" y=\"").append((int) a[1] - 32)
                        .append("\" font-size=\"10\" fill=\"#b45309\" font-family=\"monospace\">")
                        .append(escape(guard)).append(" ↩</text>");
            } else {
                svg.append("<path d=\"M").append((int) a[0]).append(',').append((int) a[1] - 26)
                        .append(" Q").append(((int) (a[0] + b[0]) / 2)).append(',').append(30)
                        .append(' ').append((int) b[0]).append(',').append((int) b[1] - 26)
                        .append("\" fill=\"none\" stroke=\"#b45309\" stroke-width=\"2\"")
                        .append(" marker-end=\"url(#arrow)\"/>");
                double midX = (a[0] + b[0]) / 2;
                svg.append("<text x=\"").append((int) midX).append("\" y=\"48\"")
                        .append(" text-anchor=\"middle\" font-size=\"10\" fill=\"#b45309\" font-family=\"monospace\">")
                        .append(escape(guard)).append(" 返回入口</text>");
            }
        } else {
            double dx = b[0] - a[0];
            double ux = dx == 0 ? 0 : dx / Math.abs(dx);
            double x1 = a[0] + ux * 26;
            double x2 = b[0] - ux * 30;
            svg.append("<line x1=\"").append((int) x1).append("\" y1=\"").append((int) a[1])
                    .append("\" x2=\"").append((int) x2).append("\" y2=\"").append((int) b[1])
                    .append("\" stroke=\"#475569\" stroke-width=\"1.5\" marker-end=\"url(#arrow)\"/>");
            svg.append("<text x=\"").append((int) ((a[0] + b[0]) / 2)).append("\" y=\"").append((int) a[1] - 10)
                    .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"#334155\" font-family=\"monospace\">[")
                    .append(escape(guard)).append("]</text>");
        }
    }

    private String firstGuard(Model model, String from, String to) {
        return model.transitions().stream()
                .filter(t -> t.source().equals(from) && t.target().equals(to))
                .findFirst()
                .map(t -> t.guard().isBlank() ? "true" : t.guard())
                .orElse("无转移!");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
