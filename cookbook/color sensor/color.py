import board
import adafruit_tcs34725

i2c = board.I2C()
sensor = adafruit_tcs34725.TCS34725(i2c)

sensor.gain = 4
sensor.integration_time = 100

print(sensor.color)