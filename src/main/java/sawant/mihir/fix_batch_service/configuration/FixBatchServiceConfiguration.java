package sawant.mihir.fix_batch_service.configuration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.PassThroughFieldSetMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import sawant.mihir.fix_batch_service.domain.FixLog;
import sawant.mihir.fix_batch_service.domain.FixMessage;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Random;


@Configuration
public class FixBatchServiceConfiguration {

   @Bean
    FixTrafficFileReader reader(@Value("file://${HOME}/Downloads/Fix_traffic_file.txt") Resource resource){
        var delegate =  new FlatFileItemReaderBuilder<FieldSet>()
                .name("fixFileLogReaderDelegate")
                .resource(resource)
                .lineTokenizer(new DelimitedLineTokenizer())
                .fieldSetMapper(new PassThroughFieldSetMapper())
                .build();
        var reader = new FixTrafficFileReader();
        reader.setDelegate(delegate);
        return reader;

   }

   @Bean
    JdbcCursorItemReader<?> fixLogReader(DataSource dataSource){


       return new JdbcCursorItemReaderBuilder<>()
               .name("fixLogFetch")
               .dataSource(dataSource)
               .sql("select id, message_type, log from fix_log order by date_time ASC")
               .rowMapper((rs, rowNum) -> {
                   String version = "";
                   String messageType = "";
                   String senderCompId = "";
                   String targetCompId = "";
                   int messageSeqNo = 0;
                   String sendingTime = "";
                   String clientOrderId = "";
                   String systemOrderId = "";
                   String symbol = "";
                   String securityId = "";
                   String side = "";
                   int orderQty = 0;
                   double price = 0;
                   String execId = "";
                   String execType = "";
                   String orderStatus = "";

                   var fixLogId = rs.getInt(1);
                   var log = rs.getString(3);
                   var splitMessages = log.split("\\|");
                   for(var message : splitMessages){
                       var values = message.split("=");
                       switch (values[0]){
                           case "8":
                               version = values[1];
                               break;
                           case "35":
                               messageType = values[1];
                               break;
                           case "49":
                               senderCompId = values[1];
                               break;
                           case "56":
                               targetCompId = values[1];
                               break;
                           case "34":
                               messageSeqNo = Integer.parseInt(values[1]);
                               break;
                           case "52":
                               sendingTime = values[1];
                               break;
                           case "11":
                               clientOrderId = values[1];
                               break;
                           case "37":
                               systemOrderId = values[1];
                               break;
                           case "55":
                               symbol = values[1];
                               break;
                           case "48":
                               securityId = values[1];
                               break;
                           case "54":
                               side = values[1];
                               break;
                           case "38":
                               orderQty = Integer.parseInt(values[1]);
                               break;
                           case "44":
                               price = Double.parseDouble(values[1]);
                               break;
                           case "17":
                               execId = values[1];
                               break;
                           case "150":
                               execType = values[1];
                               break;
                           case "39":
                               orderStatus = values[1];
                               break;
                       }
                   }
                   var fixMessage =  new FixMessage(new Random().nextInt(0, Integer.MAX_VALUE), version,
                           messageType, senderCompId, targetCompId, messageSeqNo, sendingTime, clientOrderId,
                           systemOrderId, symbol, securityId, side, orderQty, price, execId, execType,
                           orderStatus, fixLogId);
                   return fixMessage;
               }).build();


   }

   @Bean
   ItemWriter<FixMessage> fixMessageWriter(DataSource dataSource){
       return new JdbcBatchItemWriterBuilder<FixMessage>()
               .dataSource(dataSource)
               .sql("insert into fix_message(id, version, message_type, sender_comp_id, target_comp_id, " +
                       "message_seq_no, sending_time, client_order_id, system_order_id," +
                       "symbol, security_id, side, order_qty, price, exec_id, exec_type, order_status, fix_log) values (?, ?, ?, " +
                       "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
               .assertUpdates(true)
               .itemPreparedStatementSetter(new ItemPreparedStatementSetter<FixMessage>() {
                   @Override
                   public void setValues(FixMessage item, PreparedStatement ps) throws SQLException {
                       ps.setInt(1, item.id());
                       ps.setString(2, item.version());
                       ps.setString(3, item.messageType());
                       ps.setString(4, item.senderCompId());
                       ps.setString(5, item.targetCompId());
                       ps.setInt(6, item.messageSeqNo());
                       ps.setString(7, item.sendingTime());
                       ps.setString(8, item.clientOrderId());
                       ps.setString(9, item.systemOrderId());
                       ps.setString(10, item.symbol());
                       ps.setString(11, item.securityId());
                       ps.setString(12, item.side());
                       ps.setInt(13, item.orderQty());
                       ps.setDouble(14, item.price());
                       ps.setString(15, item.execId());
                       ps.setString(16, item.execType());
                       ps.setString(17, item.orderStatus());
                       ps.setInt(18, item.fixLog());
                   }
               })
               .build();
   }

   @Bean
   ItemWriter<FixLog> writer(DataSource dataSource){
       return new JdbcBatchItemWriterBuilder<FixLog>()
               .dataSource(dataSource)
               .sql("insert into fix_log(id, date_time, pid, message_type, fix_plugin, log) values (?, ?, ?, ?, ?, ?)")
               .assertUpdates(true)
               .itemPreparedStatementSetter(new ItemPreparedStatementSetter<FixLog>() {
                   @Override
                   public void setValues(FixLog item, PreparedStatement ps) throws SQLException {
                       ps.setInt(1, item.id());
                       ps.setString(2, item.fullDateTime());
                       ps.setLong(3, item.pid());
                       ps.setString(4, item.messageType());
                       ps.setString(5, item.fixPlugin());
                       ps.setString(6, item.log());
                   }
               })
               .build();
   }



   @Bean
   Step step(JobRepository repository, PlatformTransactionManager pm,
             FixTrafficFileReader reader, ItemWriter<FixLog> writer){
       return new StepBuilder("step", repository)
               .<FixLog, FixLog>chunk(25, pm)
               .reader(reader)
               .writer(writer)
               .build();

   }

    @Bean
    Step stepTwo(JobRepository repository, PlatformTransactionManager pm,
                 ItemReader<FixMessage> reader, ItemWriter<FixMessage> writer){
        return new StepBuilder("step-message", repository)
                .<FixMessage, FixMessage>chunk(25, pm)
                .reader(reader)
                .writer(writer)
                .build();

    }

   @Bean
    Job fixJob(JobRepository repository, Step step, Step stepTwo){
       return new JobBuilder("logReadJob", repository)
               .incrementer(new RunIdIncrementer())
               .start(step)
               .next(stepTwo)
               .build();
   }
}
