package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CollectionFrameworkVisualizer extends JFrame {

    public CollectionFrameworkVisualizer() {
        setTitle("Java Collection Framework Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 900);
        setLocationRelativeTo(null);

        // Создаем панель для отображения иерархии
        JPanel hierarchyPanel = new HierarchyPanel();
        JScrollPane scrollPane = new JScrollPane(hierarchyPanel);
        add(scrollPane);

        setVisible(true);
    }

    private static class HierarchyPanel extends JPanel {
        private static final int BOX_WIDTH = 180;
        private static final int BOX_HEIGHT = 40;

        private final java.util.List<Connector> connectors = new ArrayList<>();
        private final java.util.List<GroupLabel> groupLabels = new ArrayList<>();

        public HierarchyPanel() {
            setLayout(null);
            setPreferredSize(new Dimension(1300, 900));
            setBackground(new Color(240, 240, 240));

            try {
                // Читаем JSON-файл
                JsonObject jsonObject = loadJsonData();

                // Загрузка групп
                JsonArray groups = jsonObject.getAsJsonArray("groups");
                for (JsonElement groupElement : groups) {
                    JsonObject groupObj = groupElement.getAsJsonObject();
                    groupLabels.add(new GroupLabel(
                            groupObj.get("name").getAsString(),
                            groupObj.get("x").getAsInt(),
                            groupObj.get("y").getAsInt(),
                            Color.decode(groupObj.get("color").getAsString())
                    ));
                }

                // Загружаем иерархию интерфейсов
                JsonArray interfaces = jsonObject.getAsJsonArray("interfaces");
                Map<String, ClassBox> classBoxes = new HashMap<>();
                for (JsonElement interfaceElement : interfaces) {
                    JsonObject interfaceObj = interfaceElement.getAsJsonObject();
                    ClassBox box = new ClassBox(
                            interfaceObj.get("name").getAsString(),
                            interfaceObj.get("x").getAsInt(),
                            interfaceObj.get("y").getAsInt(),
                            BOX_WIDTH,
                            BOX_HEIGHT,
                            true,
                            groupLabels.stream()
                                    .filter(g -> g.name.equals(interfaceObj.get("group").getAsString()))
                                    .findFirst()
                                    .get().color
                    );
                    add(box);
                    classBoxes.put(interfaceObj.get("name").getAsString(), box);
                }

                // Создаем связи между интерфейсами
                for (JsonElement interfaceElement : interfaces) {
                    JsonObject interfaceObj = interfaceElement.getAsJsonObject();
                    ClassBox childBox = classBoxes.get(interfaceObj.get("name").getAsString());
                    JsonArray parents = interfaceObj.getAsJsonArray("parents");

                    for (JsonElement parent : parents) {
                        ClassBox parentBox = classBoxes.get(parent.getAsString());
                        connectors.add(new Connector(parentBox, childBox));
                    }
                }

                // Загружаем реализации
                JsonArray implementations = jsonObject.getAsJsonArray("implementations");
                for (JsonElement implElement : implementations) {
                    JsonObject implObj = implElement.getAsJsonObject();
                    ClassBox box = new ClassBox(
                            implObj.get("name").getAsString(),
                            implObj.get("x").getAsInt(),
                            implObj.get("y").getAsInt(),
                            BOX_WIDTH,
                            BOX_HEIGHT,
                            false,
                            groupLabels.stream()
                                    .filter(g -> g.name.equals(implObj.get("group").getAsString()))
                                    .findFirst()
                                    .get().color
                    );
                    add(box);
                    classBoxes.put(implObj.get("name").getAsString(), box);

                    ClassBox parentBox = classBoxes.get(implObj.get("parent").getAsString());
                    connectors.add(new Connector(parentBox, box));
                }

            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Failed to load hierarchy data: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }

        private static JsonObject loadJsonData() throws IOException {
            String jsonContent = new String(Files.readAllBytes(Paths.get("collections_hierarchy.json")));
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
            return jsonObject;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Рисуем группировки
            for (GroupLabel group : groupLabels) {
                group.draw(g2);
            }

            // Рисуем соединения
            for (Connector connector : connectors) {
                connector.draw(g2);
            }
        }
    }

    private static class GroupLabel {
        String name;
        int x, y;
        Color color;

        public GroupLabel(String name, int x, int y, Color color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.color = color;
        }

        public void draw(Graphics2D g2) {
            g2.setColor(color);
            g2.fillRoundRect(x, y, 200, 30, 15, 15);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString(name, x + 10, y + 20);
        }
    }

    private static class ClassInfo {
        String description;
        String since;
        String[] characteristics;
        String usage;
        String performance;

        static ClassInfo load(String className) {
            try {
                String rawName = className.replaceAll("<.*>", "");
                String jsonContent = new String(Files.readAllBytes(
                        Paths.get("class_info/" + rawName + ".json")));
                return new Gson().fromJson(jsonContent, ClassInfo.class);
            } catch (IOException e) {
                return new ClassInfo(); // Возвращаем пустую информацию, если файл не найден
            }
        }
    }

    private static class ClassBox extends JPanel {

        public ClassBox(String className, int x, int y, int width, int height, boolean isInterface, Color groupColor) {
            setBounds(x, y, width, height);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            Color bgColor = isInterface
                    ? new Color(
                    Math.min(groupColor.getRed() + 40, 255),
                    Math.min(groupColor.getGreen() + 40, 255),
                    Math.min(groupColor.getBlue() + 40, 255)
            )
                    : groupColor;

            setBackground(bgColor);
            setLayout(new BorderLayout());

            JLabel label = new JLabel(className, SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 11));
            add(label, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showClassDetails(className);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(Color.BLUE, 2),
                            BorderFactory.createEmptyBorder(5, 5, 5, 5)
                    ));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setCursor(Cursor.getDefaultCursor());
                    setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(5, 5, 5, 5)
                    ));
                }
            });
        }

        private void showClassDetails(String className) {
            try {
                String rawName = className.replaceAll("<.*>", "");
                Class<?> clazz = Class.forName("java.util." + rawName);
                ClassInfo classInfo = ClassInfo.load(className);

                JDialog dialog = new JDialog();
                dialog.setTitle("Details: " + className);
                dialog.setSize(700, 800);

                // Используем JEditorPane вместо JTextArea для поддержки HTML
                JEditorPane editorPane = new JEditorPane();
                editorPane.setEditable(false);
                editorPane.setContentType("text/html");
                editorPane.setEditorKit(new HTMLEditorKit());

                // Генерация HTML из Markdown-описаний
                String htmlContent = generateHtmlContent(clazz, classInfo);
                editorPane.setText(htmlContent);

                JScrollPane scrollPane = new JScrollPane(editorPane);
                dialog.add(scrollPane);
                dialog.setVisible(true);

            } catch (ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "Class not found: " + className,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private String generateHtmlContent(Class<?> clazz, ClassInfo classInfo) {
            // Конвертируем Markdown в HTML
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();

            StringBuilder html = new StringBuilder("<html><body style='font-family: Arial; padding: 10px'>");

            // Заголовок
            html.append("<h2>").append(clazz.isInterface() ? "INTERFACE" : "CLASS").append("</h2>");
            html.append("<p><b>Package:</b> java.util<br>");
            html.append("<b>Since Java:</b> ").append(classInfo.since != null ? classInfo.since : "N/A").append("</p>");

            // Описание (Markdown -> HTML)
            if (classInfo.description != null) {
                html.append("<h3>DESCRIPTION</h3>");
                html.append(renderer.render(parser.parse(classInfo.description)));
            }

            // Характеристики
            if (classInfo.characteristics != null && classInfo.characteristics.length > 0) {
                html.append("<h3>CHARACTERISTICS</h3><ul>");
                for (String ch : classInfo.characteristics) {
                    html.append("<li>").append(ch).append("</li>");
                }
                html.append("</ul>");
            }

            // Использование (Markdown -> HTML)
            if (classInfo.usage != null) {
                html.append("<h3>USAGE</h3>");
                html.append(renderer.render(parser.parse(classInfo.usage)));
            }

            // Производительность
            if (classInfo.performance != null) {
                html.append("<h3>PERFORMANCE</h3>");
                html.append(renderer.render(parser.parse(classInfo.performance)));
            }

            // Методы
            html.append("<h3>PUBLIC METHODS</h3><pre>");
            Method[] methods = clazz.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (Modifier.isPublic(method.getModifiers())) {
                    html.append(Modifier.toString(method.getModifiers() & Modifier.methodModifiers())).append(" ");
                    html.append(method.getReturnType().getSimpleName()).append(" ");
                    html.append(method.getName()).append("(");
                    html.append(Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", ")));
                    html.append(")<br>");
                }
            }
            html.append("</pre>");

            html.append("</body></html>");
            return html.toString();
        }
    }

    private static class Connector {
        private final ClassBox from;
        private final ClassBox to;

        public Connector(ClassBox from, ClassBox to) {
            this.from = from;
            this.to = to;
        }

        public void draw(Graphics2D g2) {
            int fromX = from.getX() + from.getWidth() / 2;
            int fromY = from.getY() + from.getHeight();
            int toX = to.getX() + to.getWidth() / 2;
            int toY = to.getY();

            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1.5f));

            // Вертикальная линия от исходного блока
            int midY = fromY + (toY - fromY) / 2;
            g2.drawLine(fromX, fromY, fromX, midY);

            // Горизонтальная линия
            g2.drawLine(fromX, midY, toX, midY);

            // Вертикальная линия к целевому блоку
            g2.drawLine(toX, midY, toX, toY);

            // Стрелка
            drawArrowHead(g2, toX, toY, toX, toY - 10);
        }

        private void drawArrowHead(Graphics2D g2, int x, int y, int xPrev, int yPrev) {
            double angle = Math.atan2(y - yPrev, x - xPrev);
            int arrowLength = 10;

            int x1 = x - (int) (arrowLength * Math.cos(angle - Math.PI / 6));
            int y1 = y - (int) (arrowLength * Math.sin(angle - Math.PI / 6));
            int x2 = x - (int) (arrowLength * Math.cos(angle + Math.PI / 6));
            int y2 = y - (int) (arrowLength * Math.sin(angle + Math.PI / 6));

            int[] xPoints = {x, x1, x2};
            int[] yPoints = {y, y1, y2};

            g2.fillPolygon(xPoints, yPoints, 3);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CollectionFrameworkVisualizer::new);
    }
}