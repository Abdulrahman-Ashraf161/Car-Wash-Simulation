# Car Wash Simulation GUI
---

## 🧩 Implemented Java Classes

### 1️⃣ ServiceStation (Main Class)
- Initializes:
  - Queue
  - Semaphores
  - Mutex
- Accepts user input:
  - Number of Pumps
  - Queue Size
- Starts:
  - Producer threads (Cars)
  - Consumer thread pool (Pumps)
- Connects to the GUI

---

### 2️⃣ Semaphore (Custom Implementation)
- Implements:
  - `wait() / P()`
  - `signal() / V()`
- Used for:
  - Queue control
  - Pump access
  - Mutual exclusion

---

### 3️⃣ Car (Producer)
- Produces Cars continuously
- Interacts with:
  - Queue
  - Mutex
  - Empty & Full Semaphores

---

### 4️⃣ Pump (Consumer)
- Consumes Cars from queue
- Uses:
  - Mutex
  - Empty, Full, and Pumps Semaphores
- Controls service execution safely

---

## 🖥️ Graphical User Interface (GUI)

✅ Real-time visualization of:
- 🚘 Car arrivals
- 🅿 Queue size
- ⛽ Active pumps
- ▶️ Service start
- ✅ Service completion

✅ Dynamic indicators for:
- Waiting Cars
- Active Services
- Available Bays

✅ Color-coded states:
- Waiting
- Processing
- Finished

---

## ✅ Simulation Rules

- Queue size: **1 ≤ Size ≤ 10**
- Pumps operate concurrently
- Pump works only if:
  - A Car is available
  - A Service Bay is free
- Cars wait if:
  - The queue is full

---

## 📤 System Output Logs

✔ Car Arrives  
✔ Car Enters Queue  
✔ Pump Takes Car  
✔ Pump Acquires Service Bay  
✔ Pump Starts Service  
✔ Pump Finishes Service  
✔ Pump Releases Service Bay  

All events are shown in:
- Console output
- GUI real-time log panel

---

## ⚙️ How to Run the Project

### ✅ Requirements
- Java JDK 8+
- Any Java IDE:
  - IntelliJ IDEA
  - Eclipse
  - NetBeans

---

### 🔧 Compile
```bash
javac *.java
