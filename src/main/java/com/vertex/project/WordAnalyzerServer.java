package com.vertex.project;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


import java.util.*;

public class WordAnalyzerServer extends AbstractVerticle {

    private static final String WORDS_FILE_PATH = "/home/ali/Documents/vertex-project/words.txt";
    private ArrayList<String> words;

    @Override
    public void start(Promise<Void> startPromise) {
        loadWordsFromFile().onComplete(loadResult -> {
            if (loadResult.succeeded()) {
                words = new ArrayList<>(loadResult.result());
                startServer(startPromise);
            } else {
                startPromise.fail(loadResult.cause());
            }
        });
    }

    private Future<List<String>> loadWordsFromFile() {
        Promise<List<String>> promise = Promise.promise();
        vertx.fileSystem().readFile(WORDS_FILE_PATH, fileResult -> {
            if (fileResult.succeeded()) {
                String content = fileResult.result().toString();
                List<String> loadedWords = new ArrayList<>();
                if (!content.isEmpty()) {
                    String[] wordsArray = content.split("\\s+");
                    loadedWords = Arrays.asList(wordsArray);
                }
                promise.complete(loadedWords);
            } else {
                promise.fail(fileResult.cause());
            }
        });
        return promise.future();
    }

    private void startServer(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/analyze").handler(this::handleAnalyzeRequest);

        server.requestHandler(router).listen(8080, httpResult -> {
            if (httpResult.succeeded()) {

                startPromise.complete();
                System.out.println("Server started and listening on port 8080");
            } else {
                startPromise.fail(httpResult.cause());
            }
        });
    }

    private void handleAnalyzeRequest(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();

        JsonObject requestBody = routingContext.getBodyAsJson();
        String text = requestBody.getString("text");
        appendWordToFile(text);
        if (words.isEmpty()) {
            response.end(Json.encode(new Result(null, null)));
            return;
        }

        String closestByValue = words.stream()
                .min(Comparator.comparingInt(word -> calculateCharacterValueDifference(text, word)))
                .orElse(null);

        String closestLexical = getClosestLexicographicString(text, words);

        response.end(Json.encode(new Result(closestByValue, closestLexical)));
    }

    private void appendWordToFile(String word) {
        // This function is used to append the word to the file and add it to the list of words
        Buffer buffer = Buffer.buffer(word + "\n");
        vertx.fileSystem().open(WORDS_FILE_PATH, new OpenOptions().setAppend(true), openResult -> {
            if (openResult.succeeded()) {
                AsyncFile asyncFile = openResult.result();
                asyncFile.write(buffer, writeResult -> {
                    if (writeResult.succeeded()) {
                        System.out.println("Word appended to file: " + WORDS_FILE_PATH);
                        words.add(word);
                    } else {
                        System.err.println("Failed to append word to file: " + WORDS_FILE_PATH);
                    }
                    asyncFile.close();
                });
            } else {
                System.err.println("Failed to open file: " + WORDS_FILE_PATH);
            }
        });
    }



    private int calculateCharacterValueDifference(String inputWord, String oneWordInWords) {
        // This function is used to calculate the difference between two string variables
        int targetValue = calculateCharacterValue(oneWordInWords);
        int wordValue = calculateCharacterValue(inputWord);
        return Math.abs(targetValue - wordValue);
    }

    private int calculateCharacterValue(String word) {
        // a=1,b=2,c=3,....z=26
        // word as input and returns an integer value that represents the total value of the characters in the string.
        int value = 0;
        for (char c : word.toCharArray()) {
            value += Character.toLowerCase(c) - 'a' + 1;
        }
        return value;
    }

    public static String getClosestLexicographicString(String a, ArrayList<String> arrayList) {
        // Sort the ArrayList in lexicographic order
        Collections.sort(arrayList);

        // Initialize variables
        String closestString = "";
        int minDifference = Integer.MAX_VALUE;

        // Iterate over the sorted ArrayList
        for (String str : arrayList) {
            int difference = str.compareTo(a);
            if (difference >= 0 && difference < minDifference) {
                closestString = str;
                minDifference = difference;
            }
        }

        return closestString;
    }

    private Future<Void> saveWordsToFile() {
//        saves asynchrously the words to the file not to block the event loop
        Promise<Void> promise = Promise.promise();
        String content = String.join(" ", words);
        vertx.fileSystem().open(WORDS_FILE_PATH, new OpenOptions(), openResult -> {
            if (openResult.succeeded()) {
                AsyncFile asyncFile = openResult.result();
                asyncFile.write(Buffer.buffer(content), writeResult -> {
                    if (writeResult.succeeded()) {
                        asyncFile.close(closeResult -> {
                            if (closeResult.succeeded()) {
                                promise.complete();
                            } else {
                                promise.fail(closeResult.cause());
                            }
                        });
                    } else {
                        promise.fail(writeResult.cause());
                    }
                });
            } else {
                promise.fail(openResult.cause());
            }
        });
        return promise.future();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        saveWordsToFile().onComplete(stopPromise);
    }


    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WordAnalyzerServer());
    }

    private static class Result {
        private final String value;
        private final String lexical;

        public Result(String value, String lexical) {
            this.value = value;
            this.lexical = lexical;
        }

        public String getValue() {
            return value;
        }

        public String getLexical() {
            return lexical;
        }
    }
}
