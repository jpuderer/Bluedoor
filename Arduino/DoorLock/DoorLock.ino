#include <SoftwareSerial.h>
#include <avr/power.h>
#include <avr/sleep.h>

const byte DOOR_LOCKED_STATUS = 0x61;
const byte DOOR_UNLOCKED_STATUS = 0x62;

SoftwareSerial doorSerial(2, 4); // RX, TX

byte lastDoorStatus = DOOR_UNLOCKED_STATUS;

void setup() {
    // initialiaze the real (UART) Serial
    Serial.begin(115200);
    // initialize the SoftwareSerial port
    doorSerial.begin(9600);
    // Put TX in a high impedance state when not sending    
    pinMode(4, INPUT);
}

void writeDoorCommand(uint8_t command) {
    pinMode(4, OUTPUT);
    digitalWrite(4, 0);
    delay(30);
    digitalWrite(4, 1);
    delayMicroseconds(1200);
    doorSerial.write((uint8_t)command);
    pinMode(4, INPUT);
    
    // The doorlock seems to need a small delay to process each 
    // individual command byte
    delay(150);
}

// Put the device into sleep mode
void sleep() {
    set_sleep_mode(SLEEP_MODE_IDLE);
    
    // Set Power Reduction register to disable timer (used by SoftSerial)
    PRR = PRR | 0b00100000;
    
    power_adc_disable();
    power_spi_disable();
    power_timer0_disable();
    power_timer1_disable();
    power_timer2_disable();
    power_twi_disable();

    // Enter sleep mode
    sleep_enable();
    sleep_mode();
    
    // Return from sleep
    sleep_disable();
    
    // Re-enable timer
    PRR = PRR & 0b00000000;
    
    power_all_enable();
}

void loop() {
    bool canSleep = true;
    while (doorSerial.available()) {
        byte b = doorSerial.read();
        canSleep = false;
        // Save the last door status byte we've seen 
        if (b == DOOR_UNLOCKED_STATUS || b == DOOR_LOCKED_STATUS) {
            lastDoorStatus = b;
        }
        // send what has been received
        Serial.write(b);
    }
    while (Serial.available()) {
        byte b = Serial.read();
        canSleep = false;
        // If we receive the null byte, someone is just asking about
        // the current status of the door
        if (b == 0x0) {
            Serial.write(lastDoorStatus);
        } else {   
            // send what has been received
            writeDoorCommand(b);
        }
    }
    
    // Sleep if there is no activity on the serial port
    if (canSleep) sleep();
    
    // Wait a little while before thinking about going back to 
    // sleep
    delay(100);
}


