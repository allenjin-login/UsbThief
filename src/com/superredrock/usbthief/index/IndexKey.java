package com.superredrock.usbthief.index;

import java.nio.file.Path;

public record IndexKey(String serialNumber, Path filePath) {

}
