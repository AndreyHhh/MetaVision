package com.lycurg.metavisionfor1c;


import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.io.*;

/**
 * Возвращает надёжный ID машины, основанный на комбинации:
 * 1. MAC-адрес основного сетевого интерфейса
 * 2. Имя компьютера (hostname)
 * 3. Серийный номер диска C: (Windows) или корневого раздела (Linux)
 * 4. ID процессора (где доступно)
 */
public class MachineIdGenerator {


    public static String getMachineId() {
        StringBuilder idBuilder = new StringBuilder();

        try {
            // 1. MAC-адрес
            String mac = getMacAddress();
            if (mac != null && !mac.equals("000000000000") && !mac.equals("FFFFFFFFFFFF")) {
                idBuilder.append(mac);
            }

            // 2. Имя компьютера
            String hostname = getHostname();
            if (hostname != null && !hostname.isEmpty()) {
                idBuilder.append(hostname);
            }

            // 3. Серийный номер диска
            String diskId = getDiskSerial();
            if (diskId != null && !diskId.isEmpty()) {
                idBuilder.append(diskId);
            }

            // 4. Информация о процессоре (где доступно)
            String cpuInfo = getCpuInfo();
            if (cpuInfo != null && !cpuInfo.isEmpty()) {
                idBuilder.append(cpuInfo);
            }

            // Если ничего не собрали, используем random UUID как fallback
            if (idBuilder.length() == 0) {
                return generateFallbackId();
            }

            // Создаём хэш SHA-256 от собранных данных
            return generateHash(idBuilder.toString());

        } catch (Exception e) {
            return generateFallbackId();
        }
    }

    // === ПОДФУНКЦИИ ===

    /**
     * Получает MAC-адрес основного сетевого интерфейса
     */
    private static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();

                // Пропускаем виртуальные, отключённые и loopback интерфейсы
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }

                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    String macStr = sb.toString();

                    // Проверяем, что это не дефолтный/пустой MAC
                    if (!macStr.equals("000000000000") && !macStr.equals("FFFFFFFFFFFF")) {
                        return macStr;
                    }
                }
            }
        } catch (SocketException e) {
            // Игнорируем ошибки
        }
        return null;
    }

    /**
     * Получает имя компьютера
     */
    private static String getHostname() {
        try {
            // Сначала пробуем через InetAddress
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isEmpty() && !hostname.equals("localhost")) {
                return hostname.toUpperCase();
            }

            // Fallback: через environment variable
            String envHostname = System.getenv("COMPUTERNAME"); // Windows
            if (envHostname == null) {
                envHostname = System.getenv("HOSTNAME"); // Linux/Unix
            }

            return envHostname != null ? envHostname.toUpperCase() : "";

        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Получает серийный номер диска
     * Windows: через wmic
     * Linux: через lsblk или df
     */
    private static String getDiskSerial() {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                Process process = Runtime.getRuntime().exec(
                        new String[]{"wmic", "diskdrive", "where", "index=0", "get", "serialnumber"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0 && !line.equals("SerialNumber")) {
                        return line.replaceAll("\\s+", "");
                    }
                }

            } else if (os.contains("linux") || os.contains("mac")) {
                // Linux/Mac
                try {
                    // Пробуем получить UUID корневого раздела
                    Process process = Runtime.getRuntime().exec(
                            new String[]{"sh", "-c", "lsblk -d -o SERIAL 2>/dev/null | head -2 | tail -1"}
                    );

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream())
                    );

                    String serial = reader.readLine();
                    if (serial != null && !serial.trim().isEmpty()) {
                        return serial.trim();
                    }

                } catch (Exception e) {
                    // Альтернативный способ для Linux
                    File fstab = new File("/etc/fstab");
                    if (fstab.exists()) {
                        return String.valueOf(fstab.lastModified());
                    }
                }
            }

        } catch (Exception e) {
            // Игнорируем ошибки
        }

        return "";
    }

    /**
     * Получает информацию о процессоре
     */
    private static String getCpuInfo() {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                Process process = Runtime.getRuntime().exec(
                        new String[]{"wmic", "cpu", "get", "processorid"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0 && !line.equals("ProcessorId")) {
                        return line.replaceAll("\\s+", "");
                    }
                }

            } else if (os.contains("linux")) {
                // Linux
                Process process = Runtime.getRuntime().exec(
                        new String[]{"sh", "-c", "cat /proc/cpuinfo | grep 'serial' | head -1 | awk '{print $3}'"}
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String cpuId = reader.readLine();
                if (cpuId != null && !cpuId.trim().isEmpty()) {
                    return cpuId.trim();
                }
            }

        } catch (Exception e) {
            // Игнорируем ошибки
        }

        return "";
    }

    /**
     * Генерирует SHA-256 хэш от собранных данных
     */
    private static String generateHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // Возвращаем первые 16 символов (достаточно для ID)
            return hexString.toString().substring(0, 16).toUpperCase();

        } catch (Exception e) {
            return generateFallbackId();
        }
    }

    /**
     * Fallback ID если ничего не получилось
     */
    private static String generateFallbackId() {
        try {
            // Генерируем ID на основе user.name + os.name + user.home
            String fallbackData = System.getProperty("user.name") +
                    System.getProperty("os.name") +
                    System.getProperty("user.home");
            return generateHash(fallbackData);
        } catch (Exception e) {
            // Последний fallback - случайный UUID
            return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16).toUpperCase();
        }
    }

    /**
     * Тестовая функция
     */
    public static void main(String[] args) {
        String machineId = getMachineId();
        System.out.println("Machine ID: " + machineId);
        System.out.println("Length: " + machineId.length());

        // Дополнительная информация для отладки
        System.out.println("\nDebug info:");
        System.out.println("MAC: " + getMacAddress());
        System.out.println("Hostname: " + getHostname());
        System.out.println("Disk Serial: " + getDiskSerial());
        System.out.println("CPU Info: " + getCpuInfo());
    }


}