// ============================
//  CS241 - Assignment 2
//  Professional Car Wash Simulation
// ============================


import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.ChangeListener;

class Semaphore {
    private int value;

    public Semaphore(int value) {
        this.value = value;
    }

    public synchronized void waitSem() throws InterruptedException { // wait operation
        while (value == 0) {
            wait();
        }
        value--;
    }

    public synchronized void signalSem() { //when the station is free make signal operation to allow next car to enter
        value++;
        notify();
    }
}

// ----------------------------
// Image Panel with URL Support
// ----------------------------
class ImagePanel extends JPanel {
    private Image image;
    private int width, height;

    public ImagePanel(String imageUrl, int width, int height) {
        this.width = width;
        this.height = height;
        loadImageFromUrl(imageUrl);
        setPreferredSize(new Dimension(width, height));
        setOpaque(false);
    }

    private void loadImageFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            ImageIcon icon = new ImageIcon(url);
            image = icon.getImage();
        } catch (Exception e) {
            System.err.println("Error loading image from URL: " + imageUrl);
            image = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, width, height, this);
        } else {
            // Professional fallback
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(200, 200, 200));
            g.drawRect(0, 0, width - 1, height - 1);
            g.setColor(Color.GRAY);
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            String text = width > 30 ? "Icon" : "I";
            g.drawString(text, width/2 - 10, height/2 + 4);
        }
    }
}

// ----------------------------
// Car Class (Producer)
// ----------------------------
class Car extends Thread {
    private int id;
    private Queue<Integer> queue;
    private Semaphore empty, full, mutex;
    private SimulationGUI gui;
    private volatile boolean paused = false;
    private volatile int speedFactor = 1;

    public Car(int id, Queue<Integer> queue, Semaphore empty, Semaphore full, Semaphore mutex, SimulationGUI gui) {
        this.id = id;
        this.queue = queue;
        this.empty = empty;
        this.full = full;
        this.mutex = mutex;
        this.gui = gui;
    }

    public void setSpeedFactor(int factor) {
        this.speedFactor = Math.max(1, factor);
    }

    public void pauseCar() {
        this.paused = true;
    }

    public void resumeCar() {
        this.paused = false;
        synchronized (this) {
            notify();
        }
    }

    private void checkPaused() throws InterruptedException {
        while (paused) {
            synchronized (this) {
                wait();
            }
        }
    }

    @Override
    public void run() { //this method simulates the car arrival and updates the GUI
        try {
            checkPaused();
            gui.updateCarStatus(id, "ARRIVED");
            gui.logMessage("Car " + id + " arrived at the station");

            empty.waitSem();
            checkPaused();
            
            mutex.waitSem();
            checkPaused();

            queue.add(id);
            gui.logMessage("Car " + id + " added to queue. Queue size: " + queue.size());
            gui.updateQueueDisplay(queue);
            gui.updateCarStatus(id, "IN_QUEUE");
            gui.logMessage("Car " + id + " entered the waiting queue");

            mutex.signalSem();
            full.signalSem();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            gui.logMessage("ERROR in Car " + id + ": " + e.getMessage());
        }
    }
}

// ----------------------------
// Pump Class (Consumer)
// ----------------------------
class Pump extends Thread {
    private int id;
    private Queue<Integer> queue;
    private Semaphore empty, full, mutex, pumpSem;
    private SimulationGUI gui;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private volatile int speedFactor = 1;

    public Pump(int id, Queue<Integer> queue, Semaphore empty, Semaphore full, Semaphore mutex, Semaphore pumpSem, SimulationGUI gui) {
        this.id = id;
        this.queue = queue;
        this.empty = empty;
        this.full = full;
        this.mutex = mutex;
        this.pumpSem = pumpSem;
        this.gui = gui;
    }

    public void setSpeedFactor(int factor) {
        this.speedFactor = Math.max(1, factor);
    }

    public void pausePump() {
        this.paused = true;
    }

    public void resumePump() {
        this.paused = false;
        synchronized (this) {
            notify();
        }
    }

    public void stopPump() {
        this.running = false;
        this.interrupt();
    }

    private void checkPaused() throws InterruptedException {
        while (paused && running) {
            synchronized (this) {
                wait();
            }
        }
    }

    private void sleepWithSpeed(int baseTime) throws InterruptedException {
        int adjustedTime = (baseTime * 2) / speedFactor;
        int step = Math.max(200, adjustedTime / 10);
        for (int i = 0; i < 10 && running && !paused; i++) {
            Thread.sleep(step);
            gui.updatePumpProgress(id, (i + 1) * 10);
            checkPaused();
        }
    }

    @Override
    public void run() {  // this method simulates the pump operation and updates the GUI accordingly
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                full.waitSem();
                checkPaused();
                if (!running) break;
                
                mutex.waitSem();
                checkPaused();
                if (!running) {
                    mutex.signalSem(); // Ensure mutex is released if stopping
                    break;
                }

                Integer carId = queue.poll(); // Get the next car from the queue
                if (carId != null) {
                    gui.logMessage("Pump " + id + " took Car " + carId + ". Queue size now: " + queue.size());
                    gui.updateQueueDisplay(queue);
                    gui.updateCarStatus(carId, "AT_PUMP_" + id);
                    gui.logMessage("Pump " + id + " took Car " + carId + " from queue");

                    mutex.signalSem(); 
                    empty.signalSem();// signal that there's an empty spot in the queue

                    pumpSem.waitSem();// wait for pump to be free
                    checkPaused();
                    if (!running) {
                        pumpSem.signalSem();
                        break;
                    }

                    gui.updatePumpStatus(id, carId, true);
                    gui.updateCarStatus(carId, "WASHING_" + id);
                    gui.logMessage("Pump " + id + ": Car " + carId + " begins service at Bay " + id);

                    // Simulate washing time with speed control
                    sleepWithSpeed(8000);

                    if (running && !paused) {
                        gui.logMessage("Pump " + id + ": Car " + carId + " finishes service");
                        gui.logMessage("Pump " + id + ": Bay " + id + " is now free");
                        gui.updateCarStatus(carId, "FINISHED");
                    }
                    
                    gui.updatePumpStatus(id, -1, false);
                    pumpSem.signalSem();
                } else {
                    mutex.signalSem();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            gui.logMessage("ERROR in Pump " + id + ": " + e.getMessage());
        }
    }
}

// ----------------------------
// Professional Simulation GUI
// ----------------------------
class SimulationGUI {
    private JFrame mainFrame;
    private JTextArea logTextArea;
    private JPanel controlPanel, visualizationPanel, statusPanel;
    private JLabel queueSizeLabel, carsProcessedLabel, simulationTimeLabel;
    private AtomicInteger carsProcessed = new AtomicInteger(0);
    private JSlider speedSlider;
    private JButton startButton, pauseButton, stopButton;
    private Timer simulationTimer;
    private long startTime;
    
    // GUI Components arrays
    private JLabel[] queueSlotLabels;
    private JLabel[] pumpStatusLabels;
    private JProgressBar[] pumpProgressBars;
    private JLabel[] carStatusLabels;
    private int waitingCapacity;

    public SimulationGUI(int waitingCapacity, int pumpsCount, int totalCars) {
        this.waitingCapacity = waitingCapacity;
        initializeGUI(waitingCapacity, pumpsCount, totalCars);
        startSimulationTimer();
    }

    private void initializeGUI(int waitingCapacity, int pumpsCount, int totalCars) {
        // Create main frame
        mainFrame = new JFrame("Car Wash Simulation - Professional System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout(10, 10));
        
        // Set professional look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create panels
        controlPanel = createControlPanel();
        visualizationPanel = createVisualizationPanel(waitingCapacity, pumpsCount, totalCars);
        statusPanel = createStatusPanel();

        mainFrame.add(controlPanel, BorderLayout.NORTH);
        mainFrame.add(visualizationPanel, BorderLayout.CENTER);
        mainFrame.add(statusPanel, BorderLayout.SOUTH);

        mainFrame.pack();
        mainFrame.setSize(1400, 900);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 102, 204), 2),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        panel.setBackground(new Color(248, 250, 252));

        // Left: Title and icons
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftPanel.setOpaque(false);

        ImagePanel carIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/3073/3073477.png", 45, 35);
        leftPanel.add(carIcon);

        JLabel titleLabel = new JLabel("PROFESSIONAL CAR WASH SYSTEM");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(0, 102, 204));
        leftPanel.add(titleLabel);

        // Center: Speed control
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        centerPanel.setOpaque(false);

        ImagePanel speedIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/2088/2088615.png", 25, 25);
        centerPanel.add(speedIcon);

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        centerPanel.add(speedLabel);

        speedSlider = new JSlider(1, 10, 2);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setSnapToTicks(true);
        speedSlider.setPreferredSize(new Dimension(200, 40));
        centerPanel.add(speedSlider);

        // Right: Control buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);

        startButton = createStyledButton("Resume", new Color(40, 167, 69));
        pauseButton = createStyledButton("Pause", new Color(255, 193, 7));
        stopButton = createStyledButton("Stop", new Color(220, 53, 69));

        rightPanel.add(startButton);
        rightPanel.add(pauseButton);
        rightPanel.add(stopButton);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(color);
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 2),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Add hover effects
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        
        return button;
    }

    private JPanel createVisualizationPanel(int waitingCapacity, int pumpsCount, int totalCars) {
        JPanel panel = new JPanel(new GridLayout(1, 3, 15, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(240, 242, 245));

        // Queue Panel
        panel.add(createQueuePanel(waitingCapacity));
        
        // Pumps Panel
        panel.add(createPumpsPanel(pumpsCount));
        
        // Cars Panel
        panel.add(createCarsPanel(totalCars));

        return panel;
    }

    private JPanel createQueuePanel(int capacity) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 165, 0), 2),
                " WAITING QUEUE "
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setBackground(Color.WHITE);

        // Header with icon
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        headerPanel.setOpaque(false);
        
        ImagePanel queueIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/1828/1828841.png", 30, 30);
        headerPanel.add(queueIcon);

        queueSizeLabel = new JLabel("0/" + capacity + " cars");
        queueSizeLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        queueSizeLabel.setForeground(new Color(255, 165, 0));
        headerPanel.add(queueSizeLabel);

        panel.add(headerPanel);
        panel.add(Box.createVerticalStrut(15));

        // Initialize queueSlotLabels array
        queueSlotLabels = new JLabel[capacity];
        
        // Queue slots
        JPanel slotsPanel = new JPanel(new GridLayout(capacity, 1, 5, 5));
        slotsPanel.setBackground(Color.WHITE);
        
        for (int i = 0; i < capacity; i++) {
            JPanel slotPanel = new JPanel(new BorderLayout(5, 0));
            slotPanel.setBackground(Color.WHITE);
            
            ImagePanel carIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/3073/3073477.png", 25, 15);
            
            JLabel slotLabel = new JLabel("EMPTY", JLabel.CENTER);
            slotLabel.setOpaque(true);
            slotLabel.setBackground(new Color(248, 249, 250));
            slotLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            slotLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            slotLabel.setForeground(new Color(108, 117, 125));
            
            slotPanel.add(carIcon, BorderLayout.WEST);
            slotPanel.add(slotLabel, BorderLayout.CENTER);
            slotsPanel.add(slotPanel);
            queueSlotLabels[i] = slotLabel;
        }
        
        panel.add(slotsPanel);
        return panel;
    }

    private JPanel createPumpsPanel(int pumpsCount) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(40, 167, 69), 2),
                " SERVICE BAYS - " + pumpsCount + " ACTIVE "
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setBackground(Color.WHITE);

        JPanel pumpsContainer = new JPanel();
        pumpsContainer.setLayout(new BoxLayout(pumpsContainer, BoxLayout.Y_AXIS));
        pumpsContainer.setBackground(Color.WHITE);

        pumpStatusLabels = new JLabel[pumpsCount];
        pumpProgressBars = new JProgressBar[pumpsCount];

        for (int i = 0; i < pumpsCount; i++) {
            JPanel pumpPanel = createSinglePumpPanel(i + 1);
            pumpsContainer.add(pumpPanel);
            pumpsContainer.add(Box.createVerticalStrut(10));
        }

        JScrollPane scrollPane = new JScrollPane(pumpsContainer);
        scrollPane.setPreferredSize(new Dimension(350, 500));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSinglePumpPanel(int pumpId) {
        JPanel pumpPanel = new JPanel(new BorderLayout(10, 0));
        pumpPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        pumpPanel.setBackground(new Color(248, 249, 250));

        // Left: Pump icon and ID
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        ImagePanel pumpIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/2838/2838694.png", 40, 40);
        pumpIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel pumpIdLabel = new JLabel("BAY " + pumpId, JLabel.CENTER);
        pumpIdLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pumpIdLabel.setForeground(new Color(108, 117, 125));
        pumpIdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        leftPanel.add(pumpIcon);
        leftPanel.add(Box.createVerticalStrut(5));
        leftPanel.add(pumpIdLabel);

        // Right: Status and progress
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel statusLabel = new JLabel("READY", JLabel.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(108, 117, 125));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Waiting...");
        progressBar.setForeground(new Color(40, 167, 69));
        progressBar.setBackground(new Color(233, 236, 239));
        progressBar.setPreferredSize(new Dimension(180, 20));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        rightPanel.add(statusLabel);
        rightPanel.add(Box.createVerticalStrut(8));
        rightPanel.add(progressBar);

        pumpPanel.add(leftPanel, BorderLayout.WEST);
        pumpPanel.add(rightPanel, BorderLayout.CENTER);

        int index = pumpId - 1;
        if (index < pumpStatusLabels.length) {
            pumpStatusLabels[index] = statusLabel;
            pumpProgressBars[index] = progressBar;
        }

        return pumpPanel;
    }

    private JPanel createCarsPanel(int totalCars) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 123, 255), 2),
                " VEHICLE STATUS - " + totalCars + " CARS "
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setBackground(Color.WHITE);

        int cols = 3;
        int rows = (int) Math.ceil(totalCars / (double) cols);
        JPanel carsGrid = new JPanel(new GridLayout(rows, cols, 8, 8));
        carsGrid.setBackground(Color.WHITE);

        carStatusLabels = new JLabel[totalCars];
        for (int i = 0; i < totalCars; i++) {
            JPanel carPanel = new JPanel(new BorderLayout(3, 3));
            carPanel.setBackground(Color.WHITE);

            ImagePanel carIcon = new ImagePanel("https://cdn-icons-png.flaticon.com/512/3073/3073477.png", 30, 20);
            carIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel carLabel = new JLabel("Car " + (i+1), JLabel.CENTER);
            carLabel.setOpaque(true);
            carLabel.setBackground(new Color(255, 193, 7));
            carLabel.setForeground(Color.BLACK);
            carLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(253, 126, 20), 1),
                BorderFactory.createEmptyBorder(8, 5, 8, 5)
            ));
            carLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));

            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);
            contentPanel.add(carIcon);
            contentPanel.add(Box.createVerticalStrut(3));
            contentPanel.add(carLabel);

            carPanel.add(contentPanel, BorderLayout.CENTER);
            carsGrid.add(carPanel);
            carStatusLabels[i] = carLabel;
        }

        JScrollPane scrollPane = new JScrollPane(carsGrid);
        scrollPane.setPreferredSize(new Dimension(300, 400));
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(" SYSTEM STATUS "),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        panel.setBackground(new Color(248, 249, 250));

        // Left: Statistics
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        statsPanel.setOpaque(false);

        carsProcessedLabel = new JLabel("Cars Processed: 0");
        carsProcessedLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        carsProcessedLabel.setForeground(new Color(40, 167, 69));

        simulationTimeLabel = new JLabel("Running Time: 00:00:00");
        simulationTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        simulationTimeLabel.setForeground(new Color(0, 123, 255));

        statsPanel.add(carsProcessedLabel);
        statsPanel.add(new JSeparator(SwingConstants.VERTICAL));
        statsPanel.add(simulationTimeLabel);

        // Center: Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setOpaque(false);

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logTextArea.setBackground(new Color(253, 253, 254));
        logTextArea.setMargin(new Insets(5, 8, 5, 8));
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);
        
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(500, 100));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230)));

        logPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(statsPanel, BorderLayout.WEST);
        panel.add(logPanel, BorderLayout.CENTER);

        return panel;
    }

    private void startSimulationTimer() {
        startTime = System.currentTimeMillis();
        simulationTimer = new Timer(1000, e -> updateSimulationTime());
        simulationTimer.start();
    }

    private void updateSimulationTime() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - startTime;
        long hours = elapsed / 3600000;
        long minutes = (elapsed % 3600000) / 60000;
        long seconds = (elapsed % 60000) / 1000;
        
        String timeString = String.format("Running Time: %02d:%02d:%02d", hours, minutes, seconds);
        simulationTimeLabel.setText(timeString);
    }

    // Public methods for simulation control
    public void setControlListeners(ActionListener startListener, ActionListener pauseListener, ActionListener stopListener) {
        startButton.addActionListener(startListener);
        pauseButton.addActionListener(pauseListener);
        stopButton.addActionListener(stopListener);
    }

    public void addSpeedChangeListener(ChangeListener listener) {
        speedSlider.addChangeListener(listener);
    }

    public int getSpeedFactor() {
        return speedSlider.getValue();
    }

    public void updateQueueDisplay(Queue<Integer> queue) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (queueSlotLabels == null) return;
                
                queueSizeLabel.setText(queue.size() + "/" + waitingCapacity + " cars");
                
                // Clear all slots first
                for (int i = 0; i < queueSlotLabels.length; i++) {
                    queueSlotLabels[i].setText("EMPTY");
                    queueSlotLabels[i].setBackground(new Color(248, 249, 250));// light gray
                    queueSlotLabels[i].setForeground(new Color(108, 117, 125));
                    queueSlotLabels[i].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(222, 226, 230), 1),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)
                    ));
                }
                
                // Fill occupied slots with actual queue content
                Integer[] queueArray = queue.toArray(new Integer[0]);
                for (int i = 0; i < queueArray.length && i < queueSlotLabels.length; i++) {
                    if (queueArray[i] != null) {
                        queueSlotLabels[i].setText("Car " + queueArray[i]);
                        queueSlotLabels[i].setBackground(new Color(255, 243, 205));
                        queueSlotLabels[i].setForeground(new Color(133, 100, 4));
                        queueSlotLabels[i].setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(255, 193, 7), 2),
                            BorderFactory.createEmptyBorder(8, 10, 8, 10)
                        ));
                    }
                }
            } catch (Exception e) {
                logMessage("ERROR updating queue display: " + e.getMessage());
            }
        });
    }

    public void updatePumpStatus(int pumpId, int carId, boolean occupied) {
        SwingUtilities.invokeLater(() -> {
            try {
                int index = pumpId - 1;
                if (index >= 0 && index < pumpStatusLabels.length) {
                    if (occupied) {
                        pumpStatusLabels[index].setText("Car " + carId);
                        pumpStatusLabels[index].setBackground(new Color(40, 167, 69));
                        pumpProgressBars[index].setString("Washing...");
                    } else {
                        pumpStatusLabels[index].setText("READY");
                        pumpStatusLabels[index].setBackground(new Color(108, 117, 125));
                        pumpProgressBars[index].setValue(0);
                        pumpProgressBars[index].setString("Waiting...");
                    }
                }
            } catch (Exception e) {
                logMessage("ERROR updating pump status: " + e.getMessage());
            }
        });
    }

    public void updatePumpProgress(int pumpId, int progress) {
        SwingUtilities.invokeLater(() -> {
            try {
                int index = pumpId - 1;
                if (index >= 0 && index < pumpProgressBars.length) {
                    pumpProgressBars[index].setValue(progress);
                    pumpProgressBars[index].setString("Washing... " + progress + "%");
                }
            } catch (Exception e) {
                logMessage("ERROR updating pump progress: " + e.getMessage());
            }
        });
    }

    public void updateCarStatus(int carId, String status) {
        SwingUtilities.invokeLater(() -> {
            try {
                int index = carId - 1;
                if (index >= 0 && index < carStatusLabels.length) {
                    Color backgroundColor;
                    Color borderColor;
                    
                    if (status.contains("ARRIVED")) {
                        backgroundColor = new Color(255, 193, 7);
                        borderColor = new Color(253, 126, 20);
                    } else if (status.contains("IN_QUEUE")) {
                        backgroundColor = new Color(255, 243, 205);
                        borderColor = new Color(255, 193, 7);
                    } else if (status.contains("AT_PUMP") || status.contains("WASHING")) {
                        backgroundColor = new Color(209, 231, 221);
                        borderColor = new Color(40, 167, 69);
                    } else if (status.contains("FINISHED")) {
                        backgroundColor = new Color(209, 229, 240);
                        borderColor = new Color(0, 123, 255);
                        carsProcessed.incrementAndGet();
                        carsProcessedLabel.setText("Cars Processed: " + carsProcessed.get());
                    } else {
                        backgroundColor = new Color(255, 193, 7);
                        borderColor = new Color(253, 126, 20);
                    }
                    
                    carStatusLabels[index].setBackground(backgroundColor);
                    carStatusLabels[index].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderColor, 1),
                        BorderFactory.createEmptyBorder(8, 5, 8, 5)
                    ));
                }
            } catch (Exception e) {
                logMessage("ERROR updating car status: " + e.getMessage());
            }
        });
    }

    public void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = java.time.LocalTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                logTextArea.append("[" + timestamp + "] " + message + "\n");
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            } catch (Exception e) {
                System.err.println("Error logging message: " + e.getMessage());
            }
        });
    }

    public void showCompletionDialog(Runnable restartCallback) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (simulationTimer != null) {
                    simulationTimer.stop();
                }
                
                int choice = JOptionPane.showConfirmDialog(mainFrame,
                    "Simulation completed successfully!\n\n" +
                    "Do you want to run another simulation?",
                    "Simulation Complete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
                
                if (choice == JOptionPane.YES_OPTION) {
                    mainFrame.dispose();
                    restartCallback.run();
                } else {
                    System.exit(0);
                }
            } catch (Exception e) {
                System.err.println("Error in completion dialog: " + e.getMessage());
                System.exit(0);
            }
        });
    }

    public void dispose() {
        try {
            if (simulationTimer != null) {
                simulationTimer.stop();
            }
            mainFrame.dispose();
        } catch (Exception e) {
            System.err.println("Error disposing GUI: " + e.getMessage());
        }
    }
}

// ----------------------------
// ServiceStation (Main Class)
// ----------------------------
class ServiceStation {
    private static Pump[] pumps;
    private static Car[] cars;
    private static Thread[] pumpThreads;
    private static Thread carGeneratorThread;
    private static SimulationGUI gui;
    private static volatile boolean simulationRunning = false;
    private static volatile boolean simulationPaused = false;
    
    private static Queue<Integer> queue;
    private static Semaphore empty, full, mutex, pumpSem;
    
    private static int waitingCapacity, pumpsCount, totalCars;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Uncaught exception in thread " + thread.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
        });
        
        SwingUtilities.invokeLater(ServiceStation::showConfigurationDialog);
    }

    private static void showConfigurationDialog() {
        while (true) {
            JPanel configPanel = new JPanel(new GridLayout(4, 2, 10, 10));
            configPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JTextField waitingField = new JTextField("3");
            JTextField pumpsField = new JTextField("2");
            JTextField carsField = new JTextField("15");

            configPanel.add(new JLabel("Waiting Area Capacity (1-10):"));
            configPanel.add(waitingField);
            configPanel.add(new JLabel("Number of Service Bays (1-10):"));
            configPanel.add(pumpsField);
            configPanel.add(new JLabel("Total Cars to Simulate (1-50):"));
            configPanel.add(carsField);
            
            JLabel noteLabel = new JLabel("<html><i>Note: Default speed is slow. Use slider to increase speed.</i></html>");
            noteLabel.setForeground(Color.GRAY);
            configPanel.add(noteLabel);
            configPanel.add(new JLabel());

            int result = JOptionPane.showConfirmDialog(null, configPanel, 
                    "Car Wash Simulation Configuration", 
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                try {
                    waitingCapacity = Integer.parseInt(waitingField.getText().trim());
                    pumpsCount = Integer.parseInt(pumpsField.getText().trim());
                    totalCars = Integer.parseInt(carsField.getText().trim());

                    if (waitingCapacity >= 1 && waitingCapacity <= 10 &&
                        pumpsCount >= 1 && pumpsCount <= 10 &&
                        totalCars >= 1 && totalCars <= 50) {
                        break;
                    } else {
                        JOptionPane.showMessageDialog(null, 
                            "Please enter valid numbers:\n" +
                            "Waiting Capacity: 1-10\n" +
                            "Service Bays: 1-10\n" +
                            "Total Cars: 1-50",
                            "Invalid Input", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, 
                        "Please enter valid numbers only.", 
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                System.exit(0);
            }
        }

        initializeSimulation();
    }

    private static void initializeSimulation() {
        try {
            // Initialize semaphores and queue
            queue = new LinkedList<>();
            empty = new Semaphore(waitingCapacity);
            full = new Semaphore(0);
            mutex = new Semaphore(1);
            pumpSem = new Semaphore(pumpsCount);

            // Initialize GUI
            gui = new SimulationGUI(waitingCapacity, pumpsCount, totalCars);
            
            // Set up control listeners
            gui.setControlListeners(
                e -> resumeSimulation(),
                e -> pauseSimulation(),
                e -> stopSimulation()
            );
            
            gui.addSpeedChangeListener(e -> updateSimulationSpeed());
            
            gui.logMessage("=== Car Wash Simulation Started ===");
            gui.logMessage("Configuration: " + waitingCapacity + " waiting slots, " + 
                          pumpsCount + " service bays, " + totalCars + " total cars");
            gui.logMessage("Initializing simulation components...");

            // Initialize pumps
            pumps = new Pump[pumpsCount];
            pumpThreads = new Thread[pumpsCount];
            
            for (int i = 0; i < pumpsCount; i++) {
                pumps[i] = new Pump(i + 1, queue, empty, full, mutex, pumpSem, gui);
                pumpThreads[i] = new Thread(pumps[i], "Pump-" + (i + 1));
                pumpThreads[i].setDaemon(true);
            }

            // Initialize cars array
            cars = new Car[totalCars];
            
            gui.logMessage("All components initialized successfully");
            gui.logMessage("Starting simulation...");

            startSimulation();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, 
                "Error initializing simulation: " + e.getMessage(), 
                "Initialization Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private static void startSimulation() {
        simulationRunning = true;
        simulationPaused = false;
        
        // Start all pumps
        for (Thread pumpThread : pumpThreads) {
            pumpThread.start();
        }
        
        // Start car generator
        startCarGenerator();
        
        gui.logMessage("Simulation is now running");
        gui.logMessage("Use the speed slider to adjust simulation speed");
    }

    private static void startCarGenerator() {
        carGeneratorThread = new Thread(() -> {
            try {
                for (int i = 0; i < totalCars && simulationRunning; i++) {
                    // Check if simulation is paused
                    while (simulationPaused && simulationRunning) {
                        Thread.sleep(100);
                    }
                    
                    if (!simulationRunning) break;
                    
                    int carId = i + 1;
                    cars[i] = new Car(carId, queue, empty, full, mutex, gui);
                    Thread carThread = new Thread(cars[i], "Car-" + carId);
                    carThread.setDaemon(true);
                    carThread.start();
                    
                    gui.logMessage("Generated Car " + carId);
                    
                    // Adjust arrival interval based on speed
                    int speedFactor = gui.getSpeedFactor();
                    int arrivalInterval = Math.max(1000, 3000 / speedFactor);
                    Thread.sleep(arrivalInterval);
                }
                
                // Wait for all cars to be processed
                gui.logMessage("All cars have been generated. Waiting for completion...");
                waitForCompletion();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                gui.logMessage("Car generator interrupted");
            } catch (Exception e) {
                gui.logMessage("ERROR in car generator: " + e.getMessage());
            }
        }, "CarGenerator");
        
        carGeneratorThread.setDaemon(true);
        carGeneratorThread.start();
    }

    private static void waitForCompletion() {
        new Thread(() -> {
            try {
                // Wait for queue to be empty and all pumps to be idle
                while (simulationRunning) {
                    Thread.sleep(1000);
                    boolean queueEmpty = queue.isEmpty();
                    boolean allPumpsIdle = true;
                    
                    // Check if all pumps are idle (no cars being processed)
                    for (int i = 0; i < pumpsCount; i++) {
                        // This is a simplified check - in a real implementation you'd track pump state
                    }
                    
                    if (queueEmpty) {
                        // Additional wait to ensure all processing is complete
                        Thread.sleep(2000);
                        if (queue.isEmpty()) {
                            break;
                        }
                    }
                }
                
                if (simulationRunning) {
                    gui.logMessage("=== Simulation Completed Successfully ===");
                    gui.showCompletionDialog(ServiceStation::showConfigurationDialog);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "CompletionMonitor").start();
    }

    private static void pauseSimulation() {
        if (simulationRunning && !simulationPaused) {
            simulationPaused = true;
            gui.logMessage("Simulation PAUSED");
            
            // Pause all cars
            for (int i = 0; i < totalCars; i++) {
                if (cars[i] != null) {
                    cars[i].pauseCar();
                }
            }
            
            // Pause all pumps
            for (int i = 0; i < pumpsCount; i++) {
                if (pumps[i] != null) {
                    pumps[i].pausePump();
                }
            }
        }
    }

    private static void resumeSimulation() {
        if (simulationRunning && simulationPaused) {
            simulationPaused = false;
            gui.logMessage("Simulation RESUMED");
            
            // Resume all cars
            for (int i = 0; i < totalCars; i++) {
                if (cars[i] != null) {
                    cars[i].resumeCar();
                }
            }
            
            // Resume all pumps
            for (int i = 0; i < pumpsCount; i++) {
                if (pumps[i] != null) {
                    pumps[i].resumePump();
                }
            }
        }
    }

    private static void stopSimulation() {
        simulationRunning = false;
        simulationPaused = false;
        
        gui.logMessage("Stopping simulation...");
        
        // Stop car generator
        if (carGeneratorThread != null) {
            carGeneratorThread.interrupt();
        }
        
        // Stop all pumps
        for (int i = 0; i < pumpsCount; i++) {
            if (pumps[i] != null) {
                pumps[i].stopPump();
            }
        }
        
        // Interrupt pump threads
        for (Thread pumpThread : pumpThreads) {
            if (pumpThread != null) {
                pumpThread.interrupt();
            }
        }
        
        gui.logMessage("Simulation stopped");
        
        // Show restart dialog
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(null,
                "Simulation stopped.\nDo you want to restart with new configuration?",
                "Simulation Stopped",
                JOptionPane.YES_NO_OPTION);
            
            if (choice == JOptionPane.YES_OPTION) {
                gui.dispose();
                showConfigurationDialog();
            } else {
                System.exit(0);
            }
        });
    }

    private static void updateSimulationSpeed() {
        if (!simulationRunning) return;
        
        int speedFactor = gui.getSpeedFactor();
        gui.logMessage("Simulation speed set to: " + speedFactor + "x");
        
        // Update all cars
        for (int i = 0; i < totalCars; i++) {
            if (cars[i] != null) {
                cars[i].setSpeedFactor(speedFactor);
            }
        }
        
        // Update all pumps
        for (int i = 0; i < pumpsCount; i++) {
            if (pumps[i] != null) {
                pumps[i].setSpeedFactor(speedFactor);
            }
        }
    }
}


