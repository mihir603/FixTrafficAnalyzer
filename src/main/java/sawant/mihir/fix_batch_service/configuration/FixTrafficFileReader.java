package sawant.mihir.fix_batch_service.configuration;


import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.transform.FieldSet;
import sawant.mihir.fix_batch_service.domain.FixLog;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class FixTrafficFileReader implements ItemReader<FixLog>, ItemStream {
    private FlatFileItemReader<FieldSet> delegate;

    @Override
    public FixLog read() throws Exception{
        for(FieldSet line; (line = this.delegate.read()) != null;){
           var chars = line.readString(0).split(" ");
            if(chars.length == 17){
                var dateStringBuilder = new StringBuilder();
                var dateTimeString = dateStringBuilder.append(chars[1]).append(" ".concat(chars[2]))
                        .append(" ".concat(chars[3])).append(" ".concat(chars[4])).append(" ".concat(chars[5]))
                        .append(" ".concat(chars[6]));
                var formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy z");
                var dateTimeZoneString = ZonedDateTime.parse(dateTimeString, formatter);
                var pid = 0L;
                try{
                    pid = Long.parseLong(chars[10]);
                }catch(NumberFormatException ex){};
                var plugin = "NA";
                if(chars[11].startsWith("OmneTradeFixServer")){
                    plugin = chars[11];
                } else{
                    plugin = chars[10];
                }
                var msgType = chars[14];
                var log = chars[16];
                return new FixLog(new Random().nextInt(10, Integer.MAX_VALUE), dateTimeZoneString.toString(), pid, msgType, plugin, log);
            } else{
                var dateStringBuilder = new StringBuilder();
                var dateTimeString = dateStringBuilder.append(chars[1]).append(" ".concat(chars[2]))
                        .append(" ".concat(chars[3])).append(" ".concat(chars[4])).append(" ".concat(chars[5]))
                        .append(" ".concat(chars[6]));
                var formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy z");
                var dateTimeZoneString = ZonedDateTime.parse(dateTimeString, formatter);
                var pid = 0L;
                try{
                    pid = Long.parseLong(chars[9]);
                }catch(NumberFormatException ex){};
                var plugin = "NA";
                if(chars[10].startsWith("OmneTradeFixServer")){
                    plugin = chars[10];
                } else{
                    plugin = chars[9];
                }
                var msgType = chars[13];
                var log = chars[15];
                return new FixLog(new Random().nextInt(10, Integer.MAX_VALUE), dateTimeZoneString.toString(), pid, msgType, plugin, log);
            }
        }
        return null;
    }

    public void setDelegate(FlatFileItemReader<FieldSet> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
       delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
       delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
       delegate.close();
    }
}
