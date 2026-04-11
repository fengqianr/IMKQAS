package com.student.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.grpc.GetVersionResponse;
import io.milvus.param.RpcStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus向量数据库配置类
 * 配置Milvus客户端连接和集合创建
 *
 * @author 系统
 * @version 1.0
 */
@Configuration
@Slf4j
public class MilvusConfig {

    @Value("${imkqas.vector-db.milvus.host:localhost}")
    private String host;

    @Value("${imkqas.vector-db.milvus.port:19530}")
    private int port;

    @Value("${imkqas.vector-db.milvus.collection:medical_documents}")
    private String collectionName;

    @Value("${imkqas.rag.embedding.dimension:1024}")
    private int dimension;

    /**
     * 创建Milvus客户端
     *
     * @return MilvusClient实例
     */
    @Bean
    public MilvusClient milvusClient() {
        try {
            log.info("初始化Milvus客户端: host={}, port={}, collection={}", host, port, collectionName);

            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build();

            MilvusClient client = new MilvusServiceClient(connectParam);

            // 测试连接
            boolean isConnected = testConnection(client);
            if (!isConnected) {
                log.warn("Milvus连接测试失败，但客户端已创建");
            } else {
                log.info("Milvus连接测试成功");
            }

            // 确保集合存在
            ensureCollectionExists(client);

            return client;
        } catch (Exception e) {
            log.error("Milvus客户端初始化失败", e);
            throw new RuntimeException("Milvus客户端初始化失败", e);
        }
    }

    /**
     * 测试Milvus连接
     *
     * @param client Milvus客户端
     * @return 连接是否成功
     */
    private boolean testConnection(MilvusClient client) {
        try {
            R<GetVersionResponse> response = client.getVersion();
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus版本: {}", response.getData().getVersion());
                return true;
            } else {
                log.warn("Milvus连接测试失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.warn("Milvus连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 确保集合存在，不存在则创建
     *
     * @param client Milvus客户端
     */
    private void ensureCollectionExists(MilvusClient client) {
        try {
            // 检查集合是否存在
            R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
            if (hasCollection.getData() != null && hasCollection.getData()) {
                log.info("Milvus集合已存在: {}", collectionName);
                return;
            }

            log.info("创建Milvus集合: {}", collectionName);

            // 定义字段
            List<FieldType> fields = new ArrayList<>();

            // 主键字段
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(io.milvus.grpc.DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();
            fields.add(idField);

            // 文档分块ID字段
            FieldType chunkIdField = FieldType.newBuilder()
                    .withName("chunk_id")
                    .withDataType(io.milvus.grpc.DataType.Int64)
                    .build();
            fields.add(chunkIdField);

            // 文档ID字段
            FieldType documentIdField = FieldType.newBuilder()
                    .withName("document_id")
                    .withDataType(io.milvus.grpc.DataType.Int64)
                    .build();
            fields.add(documentIdField);

            // 内容字段
            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(io.milvus.grpc.DataType.VarChar)
                    .withMaxLength(65535)
                    .build();
            fields.add(contentField);

            // 向量字段
            FieldType embeddingField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(io.milvus.grpc.DataType.FloatVector)
                    .withDimension(dimension)
                    .build();
            fields.add(embeddingField);

            // 元数据字段（JSON格式）
            FieldType metadataField = FieldType.newBuilder()
                    .withName("metadata")
                    .withDataType(io.milvus.grpc.DataType.VarChar)
                    .withMaxLength(65535)
                    .build();
            fields.add(metadataField);

            // 创建集合参数
            CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldTypes(fields)
                    .build();

            // 创建集合
            R<RpcStatus> createResponse = client.createCollection(createCollectionParam);
            if (createResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("创建Milvus集合失败: {}", createResponse.getMessage());
                throw new RuntimeException("创建Milvus集合失败: " + String.valueOf(createResponse.getMessage()));
            }

            log.info("Milvus集合创建成功: {}", collectionName);

            // 创建索引
            createIndex(client);

        } catch (Exception e) {
            log.error("确保Milvus集合存在失败", e);
            throw new RuntimeException("确保Milvus集合存在失败", e);
        }
    }

    /**
     * 创建向量索引
     *
     * @param client Milvus客户端
     */
    private void createIndex(MilvusClient client) {
        try {
            log.info("创建Milvus向量索引");

            CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.IP) // 内积相似度
                    .withExtraParam("{\"nlist\":1024}")
                    .build();

            R<RpcStatus> indexResponse = client.createIndex(createIndexParam);
            if (indexResponse.getStatus() != R.Status.Success.getCode()) {
                log.error("创建Milvus索引失败: {}", indexResponse.getMessage());
                throw new RuntimeException("创建Milvus索引失败: " + String.valueOf(indexResponse.getMessage()));
            }

            log.info("Milvus向量索引创建成功");
        } catch (Exception e) {
            log.error("创建Milvus索引失败", e);
            throw new RuntimeException("创建Milvus索引失败", e);
        }
    }

    // 集合名称和向量维度通过 @Value 注入，无需定义为 @Bean
}