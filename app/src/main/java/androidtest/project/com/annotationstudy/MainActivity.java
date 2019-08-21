package androidtest.project.com.annotationstudy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidtest.project.com.apt_annotation.BindView;
import androidtest.project.com.apt_library.BindViewTools;

public class MainActivity extends AppCompatActivity {

    @BindView(value = R.id.tv_1)
    TextView mTextView;
    @BindView(value = R.id.btn_2)
    Button mButton;
    @BindView(value = R.id.iv_3)
    ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BindViewTools.bind(this);
        mTextView.setText("我是 TextView");
        mButton.setText("我是 Button");
        mImageView.setImageResource(R.color.colorPrimary);
    }
}
