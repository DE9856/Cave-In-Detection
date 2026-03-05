//Executing on Processing for GUI

import processing.serial.*;

Serial myPort;

ArrayList<String> serialLines = new ArrayList<String>();
int maxLogLines = 200;

String currentLabel = "N/A";
float currentConfidence = 0;
float currentMagnitude = 0;

final int maxPoints = 100;
float[] magnitudeHistory = new float[maxPoints];
int magnitudeIndex = 0;

int leftPanelWidth;
int rightPanelX;
int graphWidth;
int graphHeight;
float graphTop;

int btnX, btnY, btnW, btnH;

void setup() {
  // Window size reduced by 70% to 1344 x 728
  size(1344, 728);
  smooth();

  frameRate(30);
  textSize(14);
  textFont(createFont("Arial", 14));

  leftPanelWidth = width / 2 - 20;
  rightPanelX = width / 2 + 10;
  graphWidth = width / 2 - 40;
  graphHeight = 200;

  btnW = 160;
  btnH = 50;
  btnX = rightPanelX + (graphWidth - btnW) / 2;
  btnY = height - btnH - 20;

  // Open serial port (update port name if needed)
  printArray(Serial.list());
  String portName = "COM7";  // change this to your ESP32 port
  myPort = new Serial(this, portName, 115200);
  myPort.bufferUntil('\n');

  for (int i = 0; i < maxPoints; i++) {
    magnitudeHistory[i] = 0;
  }
}

void serialEvent(Serial p) {
  String inString = p.readStringUntil('\n');
  if (inString != null) {
    inString = inString.trim();
    serialLines.add(inString);
    if (serialLines.size() > maxLogLines) {
      serialLines.remove(0);
    }

    // Parse vibration magnitude from lines starting with "LoRa data"
    if (inString.startsWith("LoRa data")) {
      int colonIdx = inString.indexOf(':');
      if (colonIdx != -1 && colonIdx < inString.length() - 1) {
        String valStr = inString.substring(colonIdx + 1).trim();
        try {
          float mag = Float.parseFloat(valStr);
          currentMagnitude = mag;
          magnitudeHistory[magnitudeIndex] = mag;
          magnitudeIndex = (magnitudeIndex + 1) % maxPoints;
        } catch (NumberFormatException e) {
          // ignore
        }
      }
    }

    // Parse DETECTED line for label and confidence
    if (inString.startsWith("DETECTED:")) {
      int spaceIdx = inString.indexOf(' ');
      int parenIdx = inString.indexOf('(');
      int percIdx = inString.indexOf('%');
      if (spaceIdx != -1 && parenIdx != -1 && percIdx != -1) {
        currentLabel = inString.substring(spaceIdx + 1, parenIdx).trim();
        String confStr = inString.substring(parenIdx + 1, percIdx).trim();
        try {
          currentConfidence = Float.parseFloat(confStr);
        } catch (NumberFormatException e) {
          currentConfidence = 0;
        }
      }
    }
  }
}

void draw() {
  background(245);

  // Left panel background (serial log)
  fill(230);
  rect(5, 5, leftPanelWidth, height - 10);

  // Draw serial log (left panel)
  fill(0);
  textAlign(LEFT, BOTTOM);
  float lineHeight = textAscent() + textDescent() + 2;
  // moved serial log baseline up by 25 pixels
  float baseY = height - 40;

  int maxLinesHere = min(maxLogLines, int((height - 20) / lineHeight)) - 1;  // reduced by 1 to prevent half line
  int startLine = max(0, serialLines.size() - maxLinesHere);
  for (int i = 0; i < maxLinesHere && (startLine + i) < serialLines.size(); i++) {
    String line = serialLines.get(startLine + i);
    float y = baseY - (maxLinesHere - 1 - i) * lineHeight;
    text(line, 10, y, leftPanelWidth - 20, lineHeight);
  }

  // Top left of right panel: status info
  fill(getStatusColor(currentLabel));
  textSize(26);
  textAlign(LEFT, TOP);
  float padding = 10;
  float topTextY = 20;
  text("Status: " + currentLabel, rightPanelX + padding, topTextY);

  textSize(20);
  fill(0);
  text("Confidence: " + nf(currentConfidence, 1, 2) + "%", rightPanelX + padding, topTextY + 35);
  text("Current Reading: " + nf(currentMagnitude, 1, 4), rightPanelX + padding, topTextY + 65);

  // Center graph vertically in right panel (between top margin and button)
  float rightPanelHeight = height - 10;
  float availableHeight = btnY - 20 - (topTextY + 100);
  graphTop = topTextY + 100 + (availableHeight - graphHeight) / 2;

  // Right panel graph box
  stroke(0);
  noFill();
  rect(rightPanelX, graphTop, graphWidth, graphHeight);

  // Thin horizontal grid lines in graph
  stroke(180);
  strokeWeight(1);
  for (int i = 0; i <= 6; i++) {
    float y = map(i, 0, 6, graphTop + graphHeight, graphTop);
    line(rightPanelX, y, rightPanelX + graphWidth, y);
  }

  // Y-axis labels & ticks on left side of graph
  fill(0);
  noStroke();
  textAlign(LEFT, CENTER);
  for (int i = 0; i <= 6; i++) {
    float yValue = i * 0.5;
    float y = map(i, 0, 6, graphTop + graphHeight, graphTop);
    text(nf(yValue, 1, 1), rightPanelX - 30, y);
  }

  // X-axis label under graph
  textAlign(RIGHT, TOP);
  text("Recent Vibration Magnitude", rightPanelX + graphWidth, graphTop + graphHeight + 20);

  // Draw vibration magnitude line graph
  noFill();
  stroke(255, 0, 0);
  strokeWeight(2);
  beginShape();
  for (int i = 0; i < maxPoints; i++) {
    int idx = (magnitudeIndex + i) % maxPoints;
    float mag = constrain(magnitudeHistory[idx], 0, 3.0);
    float x = map(i, 0, maxPoints - 1, rightPanelX, rightPanelX + graphWidth);
    float y = map(mag, 0, 3.0, graphTop + graphHeight, graphTop);
    vertex(x, y);
  }
  endShape();

  // Draw run inference button centered in right panel bottom
  fill(100, 150, 250);
  stroke(50, 100, 200);
  strokeWeight(2);
  rect(btnX, btnY, btnW, btnH, 14);

  fill(255);
  noStroke();
  textAlign(CENTER, CENTER);
  textSize(24);
  text("Run Inference", btnX + btnW / 2, btnY + btnH / 2);
}

color getStatusColor(String label) {
  if (label.equalsIgnoreCase("Safe")) return color(0, 150, 0);
  if (label.equalsIgnoreCase("At Risk")) return color(255, 165, 0);
  if (label.equalsIgnoreCase("Danger")) return color(255, 0, 0);
  return color(0);
}

void mousePressed() {
  if (mouseX >= btnX && mouseX <= btnX + btnW &&
      mouseY >= btnY && mouseY <= btnY + btnH) {
    if (myPort != null) {
      myPort.write("run\n");
      println("Sent 'run' command");
    }
  }
}
