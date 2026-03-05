#include <SPI.h>
#include <LoRa.h>
#include <vibrations_inferencing.h>

#define NSS 5
#define RST 14
#define DIO0 26
#define LED_PIN 2
#define BAND 433E6

float features[EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE];
uint16_t feature_ix = 0;

bool runInferenceFlag = false;

void setup() {
  Serial.begin(115200);
  while (!Serial);
  
  LoRa.setPins(NSS, RST, DIO0);
  pinMode(LED_PIN, OUTPUT);
  
  if (!LoRa.begin(BAND)) {
    Serial.println("LoRa init failed!");
    while (1);
  }
  
  Serial.println("LoRa Vibration ML Receiver");
  Serial.println("-------------------------");
  Serial.print("Expected buffer size: ");
  Serial.println(EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE);
  Serial.println("Waiting for vibration data via LoRa...");
  Serial.println("Or type 'run' to force inference with current data");
  Serial.println("-------------------------");
}

void loop() {
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    digitalWrite(LED_PIN, HIGH);
    delay(10);
    digitalWrite(LED_PIN, LOW);
    
    // Read packet
    String receivedMessage = "";
    while (LoRa.available()) {
      receivedMessage += (char)LoRa.read();
    }
    receivedMessage.trim();
    
    processReceivedData(receivedMessage);
  }
  
  if (Serial.available()) {
    String input = Serial.readStringUntil('\n');
    input.trim();
    
    if (input == "run") {
      runInferenceFlag = true;
    } else if (input == "reset") {
      feature_ix = 0;
      Serial.println("Buffer reset");
    } else if (input == "status") {
      Serial.print("Buffer status: ");
      Serial.print(feature_ix);
      Serial.print("/");
      Serial.println(EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE);
    }
  }
  
  if (runInferenceFlag) {
    Serial.print("Running inference with ");
    Serial.print(feature_ix);
    Serial.println(" data points");
    
    while (feature_ix < EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE) {
      features[feature_ix++] = 0.0;
    }
    
    runInference();
    feature_ix = 0; 
    runInferenceFlag = false;
  }
}

void processReceivedData(String message) {
  float magnitude = message.toFloat();
  if (magnitude != 0.0 || message.equals("0") || message.equals("0.0")) {
    features[feature_ix] = magnitude;
    Serial.print("LoRa data [");
    Serial.print(feature_ix + 1);
    Serial.print("/");
    Serial.print(EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE);
    Serial.print("]: ");
    Serial.println(magnitude, 6);
    
    feature_ix++;
    
    if (feature_ix >= EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE) {
      Serial.println("Buffer full, running inference...");
      runInferenceFlag = true;
    }
  } else {
    Serial.print("Received non-numeric message: ");
    Serial.println(message);
  }
}

void runInference() {
  signal_t signal;
  ei_impulse_result_t result;
  numpy::signal_from_buffer(features, EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE, &signal);
  
  EI_IMPULSE_ERROR err = run_classifier(&signal, &result, false);
  
  if (err == EI_IMPULSE_OK) {
    Serial.println("\n----- PREDICTION RESULTS -----");
    for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
      Serial.print("  ");
      Serial.print(result.classification[ix].label);
      Serial.print(": ");
      Serial.print(result.classification[ix].value * 100);
      Serial.println("%");
    }
    
    float maxValue = 0;
    size_t maxIndex = 0;
    for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
      if (result.classification[ix].value > maxValue) {
        maxValue = result.classification[ix].value;
        maxIndex = ix;
      }
    }
    
    Serial.println();
    Serial.print("DETECTED: ");
    Serial.print(result.classification[maxIndex].label);
    Serial.print(" (");
    Serial.print(maxValue * 100);
    Serial.println("%)");
    Serial.println("-----------------------------");
  } else {
    Serial.print("Error during inference: ");
    Serial.println(err);
  }
}
