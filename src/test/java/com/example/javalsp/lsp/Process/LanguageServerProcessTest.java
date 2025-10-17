package com.example.javalsp.lsp.Process;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class LanguageServerProcessTest {

    @Test
    public void testReaderThread() throws IOException, InterruptedException {
        Process mockProcess = Mockito.mock(Process.class);

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        InputStream inputStream = new PipedInputStream(pipedOutputStream);

        OutputStream outputStream = new ByteArrayOutputStream();
        InputStream errorStream = new ByteArrayInputStream(new byte[0]);

        when(mockProcess.getInputStream()).thenReturn(inputStream);
        when(mockProcess.getOutputStream()).thenReturn(outputStream);
        when(mockProcess.getErrorStream()).thenReturn(errorStream);
        when(mockProcess.isAlive()).thenReturn(true);

        BlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(1);
        Consumer<String> messageHandler = receivedMessages::offer;

        LanguageServerProcess lsp = new LanguageServerProcess(mockProcess, messageHandler, "test-user");

        String message = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
        String header = "Content-Length: " + message.length() + "\r\n\r\n";
        String fullMessage = header + message;

        pipedOutputStream.write(fullMessage.getBytes(StandardCharsets.UTF_8));
        pipedOutputStream.flush();

        String received = receivedMessages.poll(5, TimeUnit.SECONDS);

        assertEquals(message, received);

        lsp.destroy();
        inputStream.close();
        pipedOutputStream.close();
    }

    @Test
    public void testWithRealInitializeMessage() throws IOException, InterruptedException {
        Process mockProcess = Mockito.mock(Process.class);

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        InputStream inputStream = new PipedInputStream(pipedOutputStream);

        OutputStream outputStream = new ByteArrayOutputStream();
        InputStream errorStream = new ByteArrayInputStream(new byte[0]);

        when(mockProcess.getInputStream()).thenReturn(inputStream);
        when(mockProcess.getOutputStream()).thenReturn(outputStream);
        when(mockProcess.getErrorStream()).thenReturn(errorStream);
        when(mockProcess.isAlive()).thenReturn(true);

        BlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(1);
        Consumer<String> messageHandler = receivedMessages::offer;

        LanguageServerProcess lsp = new LanguageServerProcess(mockProcess, messageHandler, "test-user");

        String initializeMessage = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{\"processId\":null,\"clientInfo\":{\"name\":\"Monaco\",\"version\":\"1.69.0\"},\"locale\":\"en\",\"rootPath\":\"/Users/harsha/projects/user-feharshanew-workspace\",\"rootUri\":\"file:///Users/harsha/projects/user-feharshanew-workspace\",\"capabilities\":{\"workspace\":{\"applyEdit\":true,\"workspaceEdit\":{\"documentChanges\":true,\"resourceOperations\":[\"create\",\"rename\",\"delete\"],\"failureHandling\":\"textOnlyTransactional\",\"normalizesLineEndings\":true,\"changeAnnotationSupport\":{\"groupsOnLabel\":true}},\"codeLens\":{\"refreshSupport\":true},\"executeCommand\":{\"dynamicRegistration\":true},\"semanticTokens\":{\"refreshSupport\":true},\"inlayHint\":{\"refreshSupport\":true},\"diagnostics\":{\"refreshSupport\":true}},\"textDocument\":{\"publishDiagnostics\":{\"relatedInformation\":true,\"versionSupport\":false,\"tagSupport\":{\"valueSet\":[1,2]},\"codeDescriptionSupport\":true,\"dataSupport\":true},\"synchronization\":{\"dynamicRegistration\":true},\"completion\":{\"dynamicRegistration\":true,\"contextSupport\":true,\"completionItem\":{\"snippetSupport\":true,\"commitCharactersSupport\":true,\"documentationFormat\":[\"markdown\",\"plaintext\"],\"deprecatedSupport\":true,\"preselectSupport\":true,\"tagSupport\":{\"valueSet\":[1]},\"insertReplaceSupport\":true,\"resolveSupport\":{\"properties\":[\"documentation\",\"detail\",\"additionalTextEdits\"]},\"insertTextModeSupport\":{\"valueSet\":[1,2]},\"labelDetailsSupport\":true},\"insertTextMode\":2,\"completionItemKind\":{\"valueSet\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]},\"completionList\":{\"itemDefaults\":[\"commitCharacters\",\"editRange\",\"insertTextFormat\",\"insertTextMode\"]}},\"hover\":{\"dynamicRegistration\":true,\"contentFormat\":[\"markdown\",\"plaintext\"]},\"signatureHelp\":{\"dynamicRegistration\":true,\"signatureInformation\":{\"documentationFormat\":[\"markdown\",\"plaintext\"],\"parameterInformation\":{\"labelOffsetSupport\":true},\"activeParameterSupport\":true},\"contextSupport\":true},\"definition\":{\"dynamicRegistration\":true,\"linkSupport\":true},\"references\":{\"dynamicRegistration\":true},\"documentHighlight\":{\"dynamicRegistration\":true},\"documentSymbol\":{\"dynamicRegistration\":true,\"symbolKind\":{\"valueSet\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26]},\"hierarchicalDocumentSymbolSupport\":true,\"tagSupport\":{\"valueSet\":[1]},\"labelSupport\":true},\"codeAction\":{\"dynamicRegistration\":true,\"isPreferredSupport\":true,\"disabledSupport\":true,\"dataSupport\":true,\"resolveSupport\":{\"properties\":[\"edit\"]},\"codeActionLiteralSupport\":{\"codeActionKind\":{\"valueSet\":[\"\",\"quickfix\",\"refactor\",\"refactor.extract\",\"refactor.inline\",\"refactor.rewrite\",\"source\",\"source.organizeImports\"]}}},\"honorsChangeAnnotations\":false},\"codeLens\":{\"dynamicRegistration\":true},\"formatting\":{\"dynamicRegistration\":true},\"rangeFormatting\":{\"dynamicRegistration\":true},\"onTypeFormatting\":{\"dynamicRegistration\":true},\"rename\":{\"dynamicRegistration\":true,\"prepareSupport\":true,\"prepareSupportDefaultBehavior\":1,\"honorsChangeAnnotations\":true},\"documentLink\":{\"dynamicRegistration\":true,\"tooltipSupport\":true},\"typeDefinition\":{\"dynamicRegistration\":true,\"linkSupport\":true},\"implementation\":{\"dynamicRegistration\":true,\"linkSupport\":true},\"colorProvider\":{\"dynamicRegistration\":true},\"foldingRange\":{\"dynamicRegistration\":true,\"rangeLimit\":5000,\"lineFoldingOnly\":true,\"foldingRangeKind\":{\"valueSet\":[\"comment\",\"imports\",\"region\"]},\"foldingRange\":{\"collapsedText\":false}},\"declaration\":{\"dynamicRegistration\":true,\"linkSupport\":true},\"selectionRange\":{\"dynamicRegistration\":true},\"semanticTokens\":{\"dynamicRegistration\":true,\"tokenTypes\":[\"namespace\",\"type\",\"class\",\"enum\",\"interface\",\"struct\",\"typeParameter\",\"parameter\",\"variable\",\"property\",\"enumMember\",\"event\",\"function\",\"method\",\"macro\",\"keyword\",\"modifier\",\"comment\",\"string\",\"number\",\"regexp\",\"operator\",\"decorator\"],\"tokenModifiers\":[\"declaration\",\"definition\",\"readonly\",\"static\",\"deprecated\",\"abstract\",\"async\",\"modification\",\"documentation\",\"defaultLibrary\"],\"formats\":[\"relative\"],\"requests\":{\"range\":true,\"full\":{\"delta\":true}},\"multilineTokenSupport\":false,\"overlappingTokenSupport\":false,\"serverCancelSupport\":true,\"augmentsSyntaxTokens\":true},\"linkedEditingRange\":{\"dynamicRegistration\":true},\"inlayHint\":{\"dynamicRegistration\":true,\"resolveSupport\":{\"properties\":[\"tooltip\",\"textEdits\",\"label.tooltip\",\"label.location\",\"label.command\"]}},\"diagnostic\":{\"dynamicRegistration\":true,\"relatedDocumentSupport\":false}},\"window\":{\"showMessage\":{\"messageActionItem\":{\"additionalPropertiesSupport\":true}},\"showDocument\":{\"support\":true}},\"general\":{\"staleRequestSupport\":{\"cancel\":true,\"retryOnContentModified\":[\"textDocument/semanticTokens/full\",\"textDocument/semanticTokens/range\",\"textDocument/semanticTokens/full/delta\"]},\"regularExpressions\":{\"engine\":\"ECMAScript\",\"version\":\"ES2020\"},\"markdown\":{\"parser\":\"marked\",\"version\":\"1.1.0\"},\"positionEncodings\":[\"utf-16\"]}},\"initializationOptions\":{\"language_server.diagnostics_on_update\":true,\"language_server.diagnostics_on_save\":true,\"completion.dedupe\":true,\"completion.snippets\":true,\"completion.import_globals\":false,\"completion.limit\":100,\"index.enabled\":true,\"index.include_patterns\":[\"*.php\"],\"phpactor.language_server.method_alias_map\":true,\"phpactor.completion.dedupe_with_type\":true,\"completion.include_keywords\":false},\"trace\":\"off\",\"workspaceFolders\":[{\"uri\":\"file:///Users/harsha/projects/user-feharshanew-workspace\",\"name\":\"workspace\"}]}}";
        String header = "Content-Length: " + initializeMessage.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
        String fullMessage = header + initializeMessage;

        pipedOutputStream.write(fullMessage.getBytes(StandardCharsets.UTF_8));
        pipedOutputStream.flush();

        String received = receivedMessages.poll(5, TimeUnit.SECONDS);

        assertEquals(initializeMessage, received);

        lsp.destroy();
        inputStream.close();
        pipedOutputStream.close();
    }
}