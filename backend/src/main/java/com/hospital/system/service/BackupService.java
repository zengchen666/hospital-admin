package com.hospital.system.service;

import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCode;
import com.hospital.common.security.CurrentUser;
import com.hospital.system.dto.BackupVO;
import com.hospital.system.entity.SysBackup;
import com.hospital.system.mapper.SysBackupMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 数据备份：调用 mysqldump 生成 SQL 文件并落一条记录。
 *
 * <p><b>刻意不做「一键恢复」</b>：恢复会把当前数据库整体覆盖，
 * 界面上放一个按钮，等于把删库的能力交给一次点击 ——
 * 而且点完之后没有人能说清到底回到了哪个时间点。
 * 恢复是 DBA 拿着备份文件在维护窗口里做的事。</p>
 *
 * <p><b>密码不出现在命令行</b>：{@code mysqldump -p密码} 会让密码出现在
 * {@code ps} 输出里，同机的任何用户都能看到。这里用 {@code MYSQL_PWD}
 * 环境变量传递，虽然也不是绝对安全，但至少不落在进程参数里。</p>
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** 备份超时：库大时 dump 会很久，但也不能无限等，否则请求线程被占死 */
    private static final long TIMEOUT_MINUTES = 10;

    private final SysBackupMapper backupMapper;

    @Value("${hospital.backup.dir:./backup}")
    private String backupDir;

    /** 复用数据源配置，避免「连的是 A 库、备份的是 B 库」 */
    @Value("${spring.datasource.username:root}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${hospital.backup.mysqldump:mysqldump}")
    private String mysqldump;

    @Value("${hospital.backup.db:${DB_NAME:hospital}}")
    private String dbName;

    public BackupService(SysBackupMapper backupMapper) {
        this.backupMapper = backupMapper;
    }

    public List<BackupVO> list() {
        return backupMapper.recent().stream().map(this::toVO).toList();
    }

    /**
     * 执行一次手动备份。
     *
     * <p>备份期间会 fork 一个外部进程并等它结束，属于重操作，
     * 因此单独授权 {@code security:backup:run} 且加了超时 ——
     * 没有超时的话，一次卡住的 dump 会永久占住一个请求线程。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long run() {
        Path dir = ensureDir();
        String fileName = "hospital_" + LocalDateTime.now().format(STAMP) + ".sql";
        File target = dir.resolve(fileName).toFile();

        int exitCode = -1;
        String error = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(mysqldump);
            command.add("--single-transaction");
            command.add("--quick");
            command.add("--default-character-set=utf8mb4");
            command.add("-u");
            command.add(dbUser);
            command.add(dbName);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(target);
            builder.redirectErrorStream(false);
            // 密码走环境变量，不落在 ps 能看到的参数里
            if (dbPassword != null && !dbPassword.isBlank()) {
                builder.environment().put("MYSQL_PWD", dbPassword);
            }
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                error = "备份超时（超过 " + TIMEOUT_MINUTES + " 分钟）";
            } else {
                exitCode = process.exitValue();
                if (exitCode != 0) {
                    error = "mysqldump 退出码 " + exitCode;
                }
            }
        } catch (IOException e) {
            error = "无法执行 mysqldump：" + e.getMessage();
            log.error("备份失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "备份被中断";
        }

        SysBackup record = new SysBackup();
        record.setBackupName(fileName);
        record.setBackupPath(target.getAbsolutePath());
        record.setBackupSize(target.exists() ? target.length() : 0L);
        record.setBackupType(3);
        boolean success = error == null && target.exists() && target.length() > 0;
        record.setStatus(success ? 1 : 0);
        record.setRemark(success
                ? "手动备份 by " + CurrentUser.idOrNull()
                : (error == null ? "备份文件为空" : error));
        record.setCreateTime(LocalDateTime.now());
        backupMapper.insert(record);

        if (!success) {
            log.error("备份失败 file={} reason={}", fileName, error);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "备份失败：" + (error == null ? "备份文件为空" : error));
        }
        log.info("备份完成 file={} size={} bytes", fileName, record.getBackupSize());
        return record.getId();
    }

    /**
     * 删除备份记录并清理文件。
     *
     * <p>先删文件再删记录：反过来的话，中途失败会留下一条
     * 指向已不存在文件的记录，使用者会以为备份还在。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysBackup record = backupMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NO_RESOURCE, "备份记录不存在");
        }
        if (record.getBackupPath() != null) {
            try {
                Files.deleteIfExists(Path.of(record.getBackupPath()));
            } catch (IOException e) {
                log.warn("备份文件删除失败 path={}", record.getBackupPath(), e);
                throw new BusinessException(ErrorCode.SERVER_ERROR, "备份文件删除失败，记录已保留");
            }
        }
        backupMapper.deleteById(id);
    }

    /* ---------------- 内部方法 ---------------- */

    private Path ensureDir() {
        try {
            Path dir = Path.of(backupDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            log.error("备份目录不可用 dir={}", backupDir, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "备份目录不可用，请检查配置");
        }
    }

    private BackupVO toVO(SysBackup record) {
        BackupVO vo = new BackupVO();
        vo.setId(String.valueOf(record.getId()));
        vo.setBackupName(record.getBackupName());
        vo.setBackupSize(record.getBackupSize());
        vo.setBackupType(record.getBackupType());
        vo.setStatus(record.getStatus());
        vo.setRemark(record.getRemark());
        vo.setSizeText(sizeText(record.getBackupSize()));
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private String sizeText(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024;
            idx++;
        }
        return String.format("%.1f %s", value, units[idx]);
    }
}
