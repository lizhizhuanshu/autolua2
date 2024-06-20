/**
  * Created by MomoLuaNative.
  * Copyright (c) 2019, Momo Group. All rights reserved.
  *
  * This source code is licensed under the MIT.
  * For the full copyright and license information,please view the LICENSE file in the root directory of this source tree.
  */
/*
 * Created by LuaView.
 * Copyright (c) 2017, Alibaba Group. All rights reserved.
 *
 * This source code is licensed under the MIT.
 * For the full copyright and license information,please view the LICENSE file in the root directory of this source tree.
 */

package com.autolua.autolua2.mln.provider;

import android.content.Context;
import android.graphics.drawable.PictureDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.module.GlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.caverock.androidsvg.SVG;

import java.io.InputStream;

/**
 * configuration for glide
 * @author song 
 */
@com.bumptech.glide.annotation.GlideModule
public class GlideConfiguration implements GlideModule {

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // Apply options to the builder here.
        builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.register(SVG.class, PictureDrawable.class, new SvgTranscoder());
        registry.append(InputStream.class,SVG.class, new SvgDecoder());
    }

    private static class SvgTranscoder implements ResourceTranscoder<SVG, PictureDrawable> {
        @Nullable
        @Override
        public Resource<PictureDrawable> transcode(@NonNull Resource<SVG> toTranscode, @NonNull Options options) {
            PictureDrawable drawable = new PictureDrawable(toTranscode.get().renderToPicture());
            return new SimpleResource<>(drawable);
        }
    }

    private static class SvgDecoder implements com.bumptech.glide.load.ResourceDecoder<InputStream, SVG> {
        @Override
        public boolean handles(InputStream source, Options options) {
            return true;
        }

        @Nullable
        @Override
        public Resource<SVG> decode(@NonNull InputStream source, int width, int height, Options options) {
            try {
                SVG svg = SVG.getFromInputStream(source);
                if(width != Target.SIZE_ORIGINAL)
                    svg.setDocumentWidth(width);
                if(height != Target.SIZE_ORIGINAL)
                    svg.setDocumentHeight(height);
                return new SimpleResource<>(svg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}