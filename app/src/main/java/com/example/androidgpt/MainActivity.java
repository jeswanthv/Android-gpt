package com.example.androidgpt;

import androidx.appcompat.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;

public class MainActivity extends AppCompatActivity {

    private EditText editTextPrompt;
    private Button buttonSend;
    private Button buttonCancel;
    private TextView textViewResponseContent;
    private ChatGPTTask chatGPTTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String prompt = editTextPrompt.getText().toString();
                if (chatGPTTask != null) {
                    chatGPTTask.cancel(true);
                }
                chatGPTTask = new ChatGPTTask();
                chatGPTTask.execute(prompt);
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelTask();
            }
        });
    }

    private void initializeViews() {
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        buttonCancel = findViewById(R.id.buttonCancel);
        textViewResponseContent = findViewById(R.id.textViewResponseContent);
    }

    private static final String OpenAi = "dummy";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private class ChatGPTTask extends AsyncTask<String, Void, String> {
        private static final int MAX_RETRIES = 3;
        private static final long INITIAL_BACKOFF_DELAY = 1000; // 1 second
        private long backoffDelay = INITIAL_BACKOFF_DELAY;

        @Override
        protected String doInBackground(String... prompts) {
            if (prompts.length == 0 || prompts[0].isEmpty()) {
                return "Please enter a prompt.";
            }
            OkHttpClient client = new OkHttpClient();
            String jsonPayload = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"system\",\"content\":\"You are a helpful assistant.\"},{\"role\":\"user\",\"content\":\"" + prompts[0].replace("\"", "\\\"") + "\"}]}";
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + OpenAi)
                    .post(body)
                    .build();

            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JSONObject jsonresponse = new JSONObject(responseBody);
                        JSONArray choicesArray = jsonresponse.getJSONArray("choices");
                        if (choicesArray.length() > 0) {
                            JSONObject choice = choicesArray.getJSONObject(0);
                            JSONObject message = choice.getJSONObject("message");
                            return message.getString("content");
                        } else {
                            return "Received empty choices array.";
                        }
                    } else if (response.code() == 429) { // Rate limit exceeded
                        Thread.sleep(backoffDelay);
                        backoffDelay *= 2; // Double the backoff delay for next retry
                        retryCount++;
                        continue; // Retry the request
                    } else {
                        return "Error: " + response.code() + " " + response.message();
                    }
                } catch (Exception e) {
                    return "Failed to connect: " + e.getMessage();
                }
            }
            return "Maximum retry limit reached. Please try again later.";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            textViewResponseContent.setText(result);
            chatGPTTask = null;
        }

        @Override
        protected void onCancelled() {
            textViewResponseContent.setText("Task was cancelled.");
            chatGPTTask = null;
        }
    }

    private void cancelTask() {
        if (chatGPTTask != null) {
            chatGPTTask.cancel(true);
            chatGPTTask = null;
        }
    }
}
