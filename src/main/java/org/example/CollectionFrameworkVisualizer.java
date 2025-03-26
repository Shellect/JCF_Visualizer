package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CollectionFrameworkVisualizer extends JFrame {

    public CollectionFrameworkVisualizer() {
        setTitle("Java Collection Framework Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Создаем панель для отображения иерархии
        JPanel hierarchyPanel = new HierarchyPanel();
        JScrollPane scrollPane = new JScrollPane(hierarchyPanel);
        add(scrollPane);

        setVisible(true);
    }

    private class HierarchyPanel extends JPanel {
        private static final int BOX_WIDTH = 180;
        private static final int BOX_HEIGHT = 40;

        private final java.util.List<Connector> connectors = new ArrayList<>();
        private final java.util.List<GroupLabel> groupLabels = new ArrayList<>();

        public HierarchyPanel() {
            setLayout(null);
            setPreferredSize(new Dimension(900, 1200));
            setBackground(new Color(240, 240, 240));

            try {
                // Читаем JSON-файл
                String jsonContent = new String(Files.readAllBytes(Paths.get("collections_hierarchy.json")));
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);

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

    private class ClassBox extends JPanel {
        private final String className;

        public ClassBox(String className, int x, int y, int width, int height,
                        boolean isInterface, Color groupColor) {
            this.className = className;

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
                dialog.setSize(600, 700);
                dialog.setLocationRelativeTo(null);

                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                // Добавляем основную информацию
                textArea.append((clazz.isInterface() ? "INTERFACE\n" : "CLASS\n"));
                textArea.append("Package: java.util\n");
                textArea.append("Since: " + (classInfo.since != null ? classInfo.since : "N/A") + "\n\n");

                // Добавляем описание
                if (classInfo.description != null) {
                    textArea.append("Description:\n");
                    textArea.append(wrapText(classInfo.description, 80) + "\n\n");
                }

                // Добавляем характеристики
                if (classInfo.characteristics != null && classInfo.characteristics.length > 0) {
                    textArea.append("Characteristics:\n");
                    for (String ch : classInfo.characteristics) {
                        textArea.append(" • " + ch + "\n");
                    }
                    textArea.append("\n");
                }

                // Добавляем методы
                textArea.append("Methods:\n");
                Method[] methods = clazz.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));

                for (Method method : methods) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        textArea.append("  " + Modifier.toString(method.getModifiers() & Modifier.methodModifiers()) + " ");
                        textArea.append(method.getReturnType().getSimpleName() + " ");
                        textArea.append(method.getName() + "(");

                        Class<?>[] params = method.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            textArea.append(params[i].getSimpleName());
                            if (i < params.length - 1) textArea.append(", ");
                        }
                        textArea.append(")\n");
                    }
                }

                // Добавляем поля (для классов)
                if (!clazz.isInterface()) {
                    textArea.append("\nFields:\n");
                    Arrays.stream(clazz.getDeclaredFields())
                            .filter(f -> Modifier.isPublic(f.getModifiers()))
                            .forEach(f -> {
                                textArea.append("  " + Modifier.toString(f.getModifiers() & Modifier.fieldModifiers()) + " ");
                                textArea.append(f.getType().getSimpleName() + " ");
                                textArea.append(f.getName() + "\n");
                            });
                }

                JScrollPane scrollPane = new JScrollPane(textArea);
                dialog.add(scrollPane);
                dialog.setVisible(true);

            } catch (ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "Class not found: " + className,
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private String wrapText(String text, int width) {
            StringBuilder wrapped = new StringBuilder();
            String[] words = text.split(" ");
            int lineLength = 0;

            for (String word : words) {
                if (lineLength + word.length() > width) {
                    wrapped.append("\n");
                    lineLength = 0;
                }
                wrapped.append(word).append(" ");
                lineLength += word.length() + 1;
            }

            return wrapped.toString();
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