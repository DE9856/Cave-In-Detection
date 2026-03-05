#include <SPI.h>
#include <LoRa.h>
#include <Wire.h>
#include <MPU6050.h>
#include <math.h>
// LoRa Pins
#define SCK 18
#define MISO 19
#define MOSI 23
#define NSS 5
#define RST 14
#define DIO0 26
#define LED_PIN 2 // Internal LED
MPU6050 mpu;

// LoRa Parameters
#define BAND 433E6 // Set your LoRa frequency (433E6 for 433MHz)

void setup() {
  Serial.begin(115200);
  Wire.begin(); // Initialize I2C
  mpu.initialize(); // Initialize MPU6050
  LoRa.setPins(NSS, RST, DIO0);
  pinMode(LED_PIN, OUTPUT);
  Serial.println("MPU6050 initialized. Reading values...");

  Serial.print("Initializing LoRa Sender...");
  if (!LoRa.begin(BAND)) {
    Serial.println("Failed to initialize LoRa!");
    while (1);
  }
  Serial.println("LoRa Initialized successfully.");
  int time = 0;
}

void loop() {

  
  digitalWrite(LED_PIN, HIGH);
  delay(100);
  digitalWrite(LED_PIN, LOW);
  int16_t ax, ay, az, gx, gy, gz;

  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
  float accelMag = sqrt(sq(ax) + sq(ay) + sq(az));
  accelMag = accelMag/16384;
  Serial.print(accelMag);
  Serial.print("\n");
  LoRa.beginPacket();
  LoRa.print(accelMag);
  LoRa.endPacket();
  delay(2000); // Send message every 2 seconds
}
