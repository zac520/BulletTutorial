package com.zsoft.bullettutorial.Screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.zsoft.bullettutorial.MainGame;

/**
 * Created by zac520 on 12/18/14.
 */
public class HUD extends Stage {
    public Group graphicsGroup;
    MainGame game;
    Skin skin;
    MainScreen screen;

    public HUD (MainGame myGame, MainScreen myScreen){
        game = myGame;
        screen = myScreen;

        graphicsGroup = new Group();
        skin = new Skin(Gdx.files.internal("assets/ui/defaultskin.json"));

        //create the magic button
//        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
//        textButtonStyle.up = skin.getDrawable("magicattackbutton");//up and down are the same for this image
//        textButtonStyle.down = skin.getDrawable("selectedmagicattackbutton");
        //Button magicButton = new Button(textButtonStyle);

        TextButton forwardButton = new TextButton("Forward", skin);
        forwardButton.setPosition(0,50);
        forwardButton.setWidth(100);
        forwardButton.setHeight(50);
        forwardButton.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button ) {
                //cast spell
                //MyInput.setKey(MyInput.MAGIC, true);
                screen.forward = true;
                return true;
            }
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button ) {
                //if we could not cast it before, then cancel the attempt
                //MyInput.setKey(MyInput.MAGIC, false);
                screen.forward = false;
            }
        });
        graphicsGroup.addActor(forwardButton);

        this.addActor(graphicsGroup);
    }

}
